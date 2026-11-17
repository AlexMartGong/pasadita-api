package com.pasadita.api.services.deliveryorder;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderMapper;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderSummaryDto;
import com.pasadita.api.dto.account.SalePaidAmount;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.enums.account.PaymentStatus;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.services.account.CustomerAccountService;
import com.pasadita.api.services.invoice.InvoiceService;
import com.pasadita.api.utils.DateTimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryOrderServiceImplTest {

    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SalePaymentRepository salePaymentRepository;
    @Mock private CustomerAccountService customerAccountService;
    @Mock private InvoiceService invoiceService;
    @Mock private PlatformTransactionManager transactionManager;

    private DeliveryOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeliveryOrderServiceImpl(deliveryOrderRepository, saleRepository, employeeRepository,
                new DeliveryOrderMapper(), customerRepository, salePaymentRepository, customerAccountService,
                invoiceService, transactionManager);
    }

    private DeliveryOrder pendingOrder() {
        return order(200L, 50L, "700.00", DeliveryStatus.PENDIENTE);
    }

    private static DeliveryOrder order(long orderId, long saleId, String total, DeliveryStatus status) {
        return order(orderId, saleId, total, status, DateTimeUtils.nowUtc());
    }

    private static DeliveryOrder order(long orderId, long saleId, String total, DeliveryStatus status,
                                       LocalDateTime requestDate) {
        Customer customer = Customer.builder().id(20L).name("Clienta").build();
        Sale sale = Sale.builder().id(saleId).customer(customer).total(new BigDecimal(total))
                .paid(status == DeliveryStatus.ACTIVO).build();
        return DeliveryOrder.builder().id(orderId).sale(sale).status(status).requestDate(requestDate).build();
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"ACTIVO", "PENDIENTE"})
    void manualChangeToActiveOrPendingIsRejected(DeliveryStatus targetStatus) {
        assertThatThrownBy(() -> service.changeStatus(200L, new DeliveryOrderChangeStatusDto(targetStatus)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELADO");
        verify(deliveryOrderRepository, never()).save(any());
    }

    // R14 fails before Facturapi is touched, so the note's invoice stays alive
    @Test
    void cancellingAnOrderWhoseNoteHasPaymentsIsRejectedBeforeTouchingItsInvoice() {
        DeliveryOrder deliveryOrder = pendingOrder();
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        when(salePaymentRepository.existsBySaleIdAndStatus(50L, PaymentStatus.APLICADO)).thenReturn(true);

        assertThatThrownBy(() -> service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("abonos");
        verifyNoInteractions(invoiceService, transactionManager);
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        verify(deliveryOrderRepository, never()).save(any());
    }

    @Test
    void cancellingAnOrderCancelsItsInvoiceFirstAndThenWritesUnderTheLockInReadCommitted() {
        DeliveryOrder deliveryOrder = pendingOrder();
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        when(deliveryOrderRepository.save(deliveryOrder)).thenReturn(deliveryOrder);

        service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO));

        InOrder order = inOrder(invoiceService, transactionManager, customerRepository, deliveryOrderRepository);
        order.verify(invoiceService).cancelInvoiceOfCancelledSale(50L);
        ArgumentCaptor<TransactionDefinition> transaction = ArgumentCaptor.forClass(TransactionDefinition.class);
        order.verify(transactionManager).getTransaction(transaction.capture());
        order.verify(customerRepository).findByIdForUpdate(20L);
        order.verify(deliveryOrderRepository).save(deliveryOrder);
        assertThat(transaction.getValue().getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.CANCELADO);
    }

    // Strict: an invoice Facturapi couldn't cancel keeps the order as it was
    @Test
    void facturapiFailureLeavesTheOrderAsItWas() {
        DeliveryOrder deliveryOrder = pendingOrder();
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        doThrow(new BusinessRuleException("Facturapi rechazó cancelación (HTTP 400)"))
                .when(invoiceService).cancelInvoiceOfCancelledSale(50L);

        assertThatThrownBy(() -> service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Facturapi");
        verifyNoInteractions(transactionManager);
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        verify(deliveryOrderRepository, never()).save(any());
    }

    // The known gap between the phases: a payment committed during the Facturapi call still stops the write
    @Test
    void paymentRecordedWhileTheInvoiceIsCancelledStopsTheWriteUnderTheLock() {
        DeliveryOrder deliveryOrder = pendingOrder();
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        when(salePaymentRepository.existsBySaleIdAndStatus(50L, PaymentStatus.APLICADO)).thenReturn(false, true);

        assertThatThrownBy(() -> service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("abonos");
        verify(customerRepository).findByIdForUpdate(20L);
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        verify(deliveryOrderRepository, never()).save(any());
    }

    // R14 only counts active payments, so this also covers a note whose payments were all cancelled
    @Test
    void cancellingAnOrderWithoutActivePaymentsMarksItCancelledAndUnpaid() {
        DeliveryOrder deliveryOrder = pendingOrder();
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        when(salePaymentRepository.existsBySaleIdAndStatus(50L, PaymentStatus.APLICADO)).thenReturn(false);
        when(deliveryOrderRepository.save(deliveryOrder)).thenReturn(deliveryOrder);

        Optional<DeliveryOrderResponseDto> response =
                service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO));

        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.CANCELADO);
        assertThat(deliveryOrder.getSale().getPaid()).isFalse();
        assertThat(response).hasValueSatisfying(dto -> {
            assertThat(dto.getStatus()).isEqualTo(DeliveryStatus.CANCELADO);
            assertThat(dto.getBalance()).isEqualByComparingTo("0");
            assertThat(dto.isRequestedToday()).isTrue();
        });
    }

    // The frontend merges single-order answers into the delivery list, so they carry the same flag as the list
    @Test
    void cancellingAnOrderFromAnEarlierDayAnswersThatItIsNotTodays() {
        DeliveryOrder deliveryOrder = order(200L, 50L, "700.00", DeliveryStatus.PENDIENTE,
                DateTimeUtils.nowUtc().minusDays(2));
        when(deliveryOrderRepository.findWithSaleById(200L)).thenReturn(Optional.of(deliveryOrder));
        when(deliveryOrderRepository.save(deliveryOrder)).thenReturn(deliveryOrder);

        Optional<DeliveryOrderResponseDto> response =
                service.changeStatus(200L, new DeliveryOrderChangeStatusDto(DeliveryStatus.CANCELADO));

        assertThat(response).hasValueSatisfying(dto -> assertThat(dto.isRequestedToday()).isFalse());
    }

    // A balance left on an earlier day used to vanish from the delivery list, and its amount with it
    @Test
    void pendingOrdersFromEarlierDaysAreListedWithTheirBalanceButStayOutOfTheDaysFigures() {
        LocalDateTime now = DateTimeUtils.nowUtc();
        DeliveryOrder pendingLastWeek = order(190L, 40L, "390.00", DeliveryStatus.PENDIENTE, now.minusDays(7));
        DeliveryOrder pendingTwoDaysAgo = order(195L, 45L, "500.00", DeliveryStatus.PENDIENTE, now.minusDays(2));
        DeliveryOrder paidToday = order(203L, 53L, "250.00", DeliveryStatus.ACTIVO, now);
        DeliveryOrder pendingToday = order(204L, 54L, "700.00", DeliveryStatus.PENDIENTE, now);
        when(deliveryOrderRepository.findRequestedBetweenOrWithStatus(any(), any(), eq(DeliveryStatus.PENDIENTE)))
                .thenReturn(List.of(pendingLastWeek, pendingTwoDaysAgo, paidToday, pendingToday));
        when(salePaymentRepository.sumActiveAmountsBySaleIds(List.of(40L, 45L, 53L, 54L))).thenReturn(List.of(
                new SalePaidAmount(45L, new BigDecimal("100.00")), new SalePaidAmount(54L, new BigDecimal("300.00"))));

        DeliveryOrderSummaryDto summary = service.findAll();

        List<DeliveryOrderResponseDto> orders = summary.getOrders();
        assertThat(orders).extracting(DeliveryOrderResponseDto::getId).containsExactly(190L, 195L, 203L, 204L);
        assertThat(orders).extracting(DeliveryOrderResponseDto::isRequestedToday)
                .containsExactly(false, false, true, true);
        assertThat(orders).extracting(DeliveryOrderResponseDto::getBalance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("390.00"), new BigDecimal("400.00"), BigDecimal.ZERO,
                        new BigDecimal("400.00"));
        assertThat(orders).extracting(DeliveryOrderResponseDto::isHasPayments)
                .containsExactly(false, true, false, true);
        assertThat(orders).extracting(DeliveryOrderResponseDto::getCustomerId).containsOnly(20L);
        // Only today's two orders count, and only the paid one adds to the amount
        assertThat(summary.getTotalOrders()).isEqualTo(2);
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("250.00");
        // One aggregate of payments for the whole list, whatever the day of each order
        verify(salePaymentRepository).sumActiveAmountsBySaleIds(any());
    }

    @Test
    void theListAsksForMexicosCurrentDayInUtcPlusEveryPendingOrder() {
        when(deliveryOrderRepository.findRequestedBetweenOrWithStatus(any(), any(), any())).thenReturn(List.of());
        LocalDate dayBeforeTheCall = DateTimeUtils.nowMexico().toLocalDate();

        service.findAll();

        LocalDate dayAfterTheCall = DateTimeUtils.nowMexico().toLocalDate();
        ArgumentCaptor<LocalDateTime> startOfDay = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endOfDay = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deliveryOrderRepository).findRequestedBetweenOrWithStatus(startOfDay.capture(), endOfDay.capture(),
                eq(DeliveryStatus.PENDIENTE));
        LocalDateTime startInMexico = DateTimeUtils.toMexicoTime(startOfDay.getValue());
        assertThat(startInMexico.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(startInMexico.toLocalDate()).isBetween(dayBeforeTheCall, dayAfterTheCall);
        assertThat(DateTimeUtils.toMexicoTime(endOfDay.getValue())).isEqualTo(startInMexico.plusDays(1));
    }

    @Test
    void todaysOrdersFlagActivePaymentsAndShowTheBalanceOnlyForPendingOnes() {
        DeliveryOrder settledByPayments = order(201L, 51L, "390.00", DeliveryStatus.ACTIVO);
        DeliveryOrder pending = order(202L, 52L, "700.00", DeliveryStatus.PENDIENTE);
        DeliveryOrder paidAtDelivery = order(203L, 53L, "250.00", DeliveryStatus.ACTIVO);
        when(deliveryOrderRepository.findRequestedBetweenOrWithStatus(any(), any(), eq(DeliveryStatus.PENDIENTE)))
                .thenReturn(List.of(settledByPayments, pending, paidAtDelivery));
        when(salePaymentRepository.sumActiveAmountsBySaleIds(List.of(51L, 52L, 53L))).thenReturn(List.of(
                new SalePaidAmount(51L, new BigDecimal("390.00")), new SalePaidAmount(52L, new BigDecimal("300.00"))));

        List<DeliveryOrderResponseDto> orders = service.findAll().getOrders();

        assertThat(orders).extracting(DeliveryOrderResponseDto::isHasPayments).containsExactly(true, true, false);
        assertThat(orders).extracting(DeliveryOrderResponseDto::getBalance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("400.00"), BigDecimal.ZERO);
    }

    @Test
    void orderForAnUnpaidSaleIsRejected() {
        Sale unpaidSale = Sale.builder().id(50L).customer(Customer.builder().id(20L).build()).paid(false).build();
        when(saleRepository.findById(50L)).thenReturn(Optional.of(unpaidSale));

        assertThatThrownBy(() -> service.save(DeliveryOrderCreateDto.builder().saleId(50L).build(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("venta pagada");
        verifyNoInteractions(customerAccountService);
    }

    @Test
    void newOrderChecksCustomerCreditWithTheCallerRole() {
        Sale paidSale = Sale.builder().id(50L).customer(Customer.builder().id(20L).name("Clienta").build())
                .total(new BigDecimal("390.00")).paid(true).build();
        when(saleRepository.findById(50L)).thenReturn(Optional.of(paidSale));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(Employee.builder().id(5L).build()));
        when(deliveryOrderRepository.save(any(DeliveryOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.save(DeliveryOrderCreateDto.builder()
                .saleId(50L)
                .deliveryEmployeeId(5L)
                .deliveryAddress("Calle 1")
                .contactPhone("3300000000")
                .build(), false);

        verify(customerAccountService).assertCreditAllowsNewOrder(20L, false);
    }

    @Test
    void creditCheckFailureStopsTheOrder() {
        Sale paidSale = Sale.builder().id(50L).customer(Customer.builder().id(20L).build()).paid(true).build();
        when(saleRepository.findById(50L)).thenReturn(Optional.of(paidSale));
        doThrow(new BusinessRuleException("Se requiere autorización del administrador"))
                .when(customerAccountService).assertCreditAllowsNewOrder(anyLong(), anyBoolean());

        assertThatThrownBy(() -> service.save(DeliveryOrderCreateDto.builder().saleId(50L).build(), false))
                .isInstanceOf(BusinessRuleException.class);
        verify(deliveryOrderRepository, never()).save(any());
    }
}
