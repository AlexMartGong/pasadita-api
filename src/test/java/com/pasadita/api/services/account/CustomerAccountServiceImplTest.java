package com.pasadita.api.services.account;

import com.pasadita.api.config.SalesProperties;
import com.pasadita.api.dto.account.AppliedPaymentDto;
import com.pasadita.api.dto.account.CustomerAccountMapper;
import com.pasadita.api.dto.account.DeliveryCollectionDto;
import com.pasadita.api.dto.account.PaymentCreateDto;
import com.pasadita.api.dto.account.PaymentResultDto;
import com.pasadita.api.dto.account.SalePaidAmount;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.PaymentMethod;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.entities.SalePayment;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.PaymentMethodRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceImplTest {

    private static final long WALK_IN_CUSTOMER_ID = 1L;
    private static final long CUSTOMER_ID = 20L;
    private static final long EMPLOYEE_ID = 5L;
    private static final long CASH_ID = 1L;
    private static final LocalDateTime FIRST_DAY = LocalDateTime.of(2026, 10, 1, 15, 0);

    @Mock private CustomerRepository customerRepository;
    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private SalePaymentRepository salePaymentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;

    private CustomerAccountServiceImpl service;
    private final List<SalePayment> savedPayments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CustomerAccountServiceImpl(customerRepository, deliveryOrderRepository, salePaymentRepository,
                employeeRepository, paymentMethodRepository, new CustomerAccountMapper(),
                new SalesProperties(WALK_IN_CUSTOMER_ID));

        lenient().when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(Employee.builder().id(EMPLOYEE_ID).fullName("Repartidor").build()));
        lenient().when(paymentMethodRepository.findById(CASH_ID))
                .thenReturn(Optional.of(PaymentMethod.builder().id(CASH_ID).name("Efectivo").build()));
        lenient().when(salePaymentRepository.save(any(SalePayment.class))).thenAnswer(invocation -> {
            SalePayment payment = invocation.getArgument(0);
            savedPayments.add(payment);
            return payment;
        });
    }

    // --- fixtures ---

    private Customer lockedCustomer(long customerId, String creditLimit) {
        Customer customer = Customer.builder()
                .id(customerId)
                .name("Clienta " + customerId)
                .creditLimit(new BigDecimal(creditLimit))
                .build();
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        return customer;
    }

    private DeliveryOrder order(long orderId, long saleId, Customer customer, String total, DeliveryStatus status,
                                int daysAfterFirst) {
        Sale sale = Sale.builder()
                .id(saleId)
                .customer(customer)
                .total(new BigDecimal(total))
                .paid(status == DeliveryStatus.ACTIVO)
                .datetime(FIRST_DAY.plusDays(daysAfterFirst))
                .build();
        return DeliveryOrder.builder().id(orderId).sale(sale).status(status).build();
    }

    private void beingCollected(DeliveryOrder deliveryOrder) {
        when(deliveryOrderRepository.findCustomerIdById(deliveryOrder.getId()))
                .thenReturn(Optional.of(deliveryOrder.getSale().getCustomer().getId()));
        when(deliveryOrderRepository.findWithSaleById(deliveryOrder.getId())).thenReturn(Optional.of(deliveryOrder));
    }

    private void pendingNotes(Customer customer, DeliveryOrder... oldestFirst) {
        when(deliveryOrderRepository.findByCustomerIdAndStatusOldestFirst(customer.getId(), DeliveryStatus.PENDIENTE))
                .thenReturn(Arrays.asList(oldestFirst));
    }

    private void paidAmounts(SalePaidAmount... paidAmounts) {
        when(salePaymentRepository.sumAmountsBySaleIds(anyCollection())).thenReturn(Arrays.asList(paidAmounts));
    }

    private static SalePaidAmount paid(long saleId, String amount) {
        return new SalePaidAmount(saleId, new BigDecimal(amount));
    }

    // Payment lookups read back what the service saved, the way later requests would see it in the database
    private void storedPaymentsAreVisible() {
        lenient().when(salePaymentRepository.existsBySaleId(anyLong())).thenAnswer(invocation -> savedPayments.stream()
                .anyMatch(payment -> payment.getSale().getId().equals(invocation.getArgument(0))));
        lenient().when(salePaymentRepository.existsByClientRequestId(anyString())).thenAnswer(invocation ->
                !paymentsUnder(invocation.getArgument(0)).isEmpty());
        lenient().when(salePaymentRepository.findByClientRequestIdOrderByIdAsc(anyString())).thenAnswer(invocation ->
                paymentsUnder(invocation.getArgument(0)));
    }

    private List<SalePayment> paymentsUnder(String clientRequestId) {
        return savedPayments.stream()
                .filter(payment -> clientRequestId.equals(payment.getClientRequestId()))
                .toList();
    }

    private static DeliveryCollectionDto collection(String amountReceived) {
        return collection(amountReceived, null);
    }

    private static DeliveryCollectionDto collection(String amountReceived, String clientRequestId) {
        return DeliveryCollectionDto.builder()
                .employeeId(EMPLOYEE_ID)
                .amountReceived(new BigDecimal(amountReceived))
                .paymentMethodId(CASH_ID)
                .clientRequestId(clientRequestId)
                .build();
    }

    private static PaymentCreateDto payment(String amount, String clientRequestId) {
        return PaymentCreateDto.builder()
                .employeeId(EMPLOYEE_ID)
                .amount(new BigDecimal(amount))
                .paymentMethodId(CASH_ID)
                .clientRequestId(clientRequestId)
                .build();
    }

    // --- registerDeliveryCollection ---

    @Test
    void businessExampleSurplusOfNoteCGoesToOldestPendingNoteA() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder noteA = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        DeliveryOrder noteB = order(102L, 2L, customer, "677.00", DeliveryStatus.PENDIENTE, 1);
        DeliveryOrder noteC = order(103L, 3L, customer, "390.00", DeliveryStatus.ACTIVO, 2);
        beingCollected(noteC);
        pendingNotes(customer, noteA, noteB);
        paidAmounts(paid(1L, "300.00"));

        PaymentResultDto result = service.registerDeliveryCollection(103L, collection("500"));

        assertThat(noteC.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(noteC.getSale().getPaid()).isTrue();
        assertThat(savedPayments).satisfiesExactly(
                notePayment -> {
                    assertThat(notePayment.getSale().getId()).isEqualTo(3L);
                    assertThat(notePayment.getAmount()).isEqualByComparingTo("390.00");
                    assertThat(notePayment.getNotes()).isEqualTo("Cobro en entrega del pedido #103");
                },
                surplusPayment -> {
                    assertThat(surplusPayment.getSale().getId()).isEqualTo(1L);
                    assertThat(surplusPayment.getAmount()).isEqualByComparingTo("110.00");
                    assertThat(surplusPayment.getNotes()).isEqualTo("Excedente del pedido #103");
                });
        assertThat(result.getAppliedPayments()).satisfiesExactly(
                notePayment -> {
                    assertThat(notePayment.getSaleId()).isEqualTo(3L);
                    assertThat(notePayment.getRemainingBalance()).isEqualByComparingTo("0.00");
                },
                surplusPayment -> {
                    assertThat(surplusPayment.getSaleId()).isEqualTo(1L);
                    assertThat(surplusPayment.getRemainingBalance()).isEqualByComparingTo("590.00");
                });
        assertThat(noteA.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("1267.00");
        assertThat(result.getChange()).isEqualByComparingTo("0.00");
        assertThat(result.getOrderStatus()).isEqualTo(DeliveryStatus.ACTIVO);
    }

    @Test
    void shortfallLeavesOrderPendingWithPaymentForWhatWasReceived() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "1000");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        PaymentResultDto result = service.registerDeliveryCollection(200L, collection("400"));

        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(deliveryOrder.getSale().getPaid()).isFalse();
        assertThat(savedPayments).singleElement().satisfies(payment -> {
            assertThat(payment.getSale().getId()).isEqualTo(50L);
            assertThat(payment.getAmount()).isEqualByComparingTo("400.00");
            assertThat(payment.getEmployee().getId()).isEqualTo(EMPLOYEE_ID);
            assertThat(payment.getPaymentMethod().getId()).isEqualTo(CASH_ID);
            assertThat(payment.getNotes()).isEqualTo("Cobro en entrega del pedido #200");
        });
        assertThat(result.getOrderStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("300.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("300.00");
        assertThat(result.isOverLimit()).isFalse();
    }

    @Test
    void nothingReceivedLeavesOrderPendingWithoutPaymentRows() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "1000");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        DeliveryCollectionDto nothingReceived = DeliveryCollectionDto.builder()
                .employeeId(EMPLOYEE_ID)
                .amountReceived(BigDecimal.ZERO)
                .build();
        PaymentResultDto result = service.registerDeliveryCollection(200L, nothingReceived);

        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(deliveryOrder.getSale().getPaid()).isFalse();
        assertThat(savedPayments).isEmpty();
        assertThat(result.getSaleBalance()).isEqualByComparingTo("700.00");
        assertThat(result.getAppliedPayments()).isEmpty();
    }

    @Test
    void exactAmountIsRejectedBecauseThereIsNothingToRecord() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("700.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay nada que registrar");
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void surplusSettlesSeveralNotesOldestFirstAndReturnsTheRestAsChange() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder oldest = order(101L, 1L, customer, "100.00", DeliveryStatus.PENDIENTE, 0);
        DeliveryOrder newer = order(102L, 2L, customer, "200.00", DeliveryStatus.PENDIENTE, 1);
        DeliveryOrder collected = order(103L, 3L, customer, "300.00", DeliveryStatus.ACTIVO, 2);
        beingCollected(collected);
        pendingNotes(customer, oldest, newer);
        paidAmounts(paid(2L, "50.00"));

        PaymentResultDto result = service.registerDeliveryCollection(103L, collection("700"));

        assertThat(savedPayments).extracting(payment -> payment.getSale().getId()).containsExactly(3L, 1L, 2L);
        assertThat(savedPayments).extracting(SalePayment::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("300"), new BigDecimal("100"), new BigDecimal("150"));
        assertThat(oldest.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(oldest.getSale().getPaid()).isTrue();
        assertThat(newer.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(newer.getSale().getPaid()).isTrue();
        assertThat(result.getChange()).isEqualByComparingTo("150.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("0.00");
        assertThat(result.getAppliedPayments()).extracting(AppliedPaymentDto::getRemainingBalance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void surplusWithoutDebtStoresTheNotePaymentAndItsRetryIsIdempotent() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "390.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);
        storedPaymentsAreVisible();

        PaymentResultDto result = service.registerDeliveryCollection(200L, collection("500", "req-1"));
        PaymentResultDto retry = service.registerDeliveryCollection(200L, collection("500", "req-1"));

        assertThat(savedPayments).singleElement().satisfies(payment -> {
            assertThat(payment.getSale().getId()).isEqualTo(50L);
            assertThat(payment.getAmount()).isEqualByComparingTo("390.00");
            assertThat(payment.getNotes()).isEqualTo("Cobro en entrega del pedido #200");
            assertThat(payment.getClientRequestId()).isEqualTo("req-1");
        });
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(deliveryOrder.getSale().getPaid()).isTrue();
        assertThat(result.getChange()).isEqualByComparingTo("110.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("0.00");
        assertThat(retry).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(result);
    }

    @Test
    void secondCollectionOfASurplusOrderIsRejectedEvenWithANewClientRequestId() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder oldNote = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        DeliveryOrder collected = order(103L, 3L, customer, "390.00", DeliveryStatus.ACTIVO, 2);
        beingCollected(collected);
        pendingNotes(customer, oldNote);
        storedPaymentsAreVisible();

        service.registerDeliveryCollection(103L, collection("500", "req-1"));

        assertThatThrownBy(() -> service.registerDeliveryCollection(103L, collection("500", "req-2")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene abonos");
        assertThat(savedPayments).extracting(payment -> payment.getSale().getId()).containsExactly(3L, 1L);
    }

    @Test
    void walkInCustomerCannotBeLeftWithBalance() {
        Customer walkIn = lockedCustomer(WALK_IN_CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, walkIn, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("400")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Público en general");
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void collectionIsRejectedWhenOrderIsNotActive() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        beingCollected(deliveryOrder);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("400")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no está ACTIVO");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void collectionIsRejectedWhenNoteAlreadyHasPayments() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);
        when(salePaymentRepository.existsBySaleId(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("400")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene abonos");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void shortfallOverCreditLimitIsStillRecordedAndFlagged() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "200");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        PaymentResultDto result = service.registerDeliveryCollection(200L, collection("400"));

        assertThat(savedPayments).hasSize(1);
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("300.00");
        assertThat(result.getCreditLimit()).isEqualByComparingTo("200.00");
        assertThat(result.isOverLimit()).isTrue();
    }

    @Test
    void amountsWithDifferentScaleCompareAsEqual() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("700.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay nada que registrar");
    }

    // --- registerAccountPayment ---

    @Test
    void storePaymentIsRejectedWhenCustomerHasNoDebt() {
        lockedCustomer(CUSTOMER_ID, "0");

        assertThatThrownBy(() -> service.registerAccountPayment(CUSTOMER_ID, payment("100", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no tiene deuda");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void storePaymentGoesToOldestNoteAndRestIsChange() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder oldest = order(101L, 1L, customer, "100.00", DeliveryStatus.PENDIENTE, 0);
        pendingNotes(customer, oldest);

        PaymentResultDto result = service.registerAccountPayment(CUSTOMER_ID, payment("120", "req-9"));

        assertThat(savedPayments).singleElement().satisfies(payment -> {
            assertThat(payment.getAmount()).isEqualByComparingTo("100.00");
            assertThat(payment.getNotes()).isEqualTo("Abono a cuenta");
            assertThat(payment.getClientRequestId()).isEqualTo("req-9");
        });
        assertThat(oldest.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(oldest.getSale().getPaid()).isTrue();
        assertThat(result.getChange()).isEqualByComparingTo("20.00");
        assertThat(result.getOrderStatus()).isNull();
    }

    @Test
    void repeatedClientRequestIdReturnsCurrentStateWithoutDuplicating() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder noteA = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        pendingNotes(customer, noteA);
        paidAmounts(paid(1L, "500.00"));
        SalePayment alreadyStored = SalePayment.builder()
                .id(77L).sale(noteA.getSale()).amount(new BigDecimal("200.00")).clientRequestId("req-1").build();
        when(salePaymentRepository.existsByClientRequestId("req-1")).thenReturn(true);
        when(salePaymentRepository.findByClientRequestIdOrderByIdAsc("req-1")).thenReturn(List.of(alreadyStored));

        PaymentResultDto result = service.registerAccountPayment(CUSTOMER_ID, payment("200", "req-1"));

        verify(salePaymentRepository, never()).save(any());
        assertThat(result.getAppliedPayments()).singleElement().satisfies(applied -> {
            assertThat(applied.getSaleId()).isEqualTo(1L);
            assertThat(applied.getAmount()).isEqualByComparingTo("200.00");
            assertThat(applied.getRemainingBalance()).isEqualByComparingTo("500.00");
        });
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("500.00");
        assertThat(result.getChange()).isEqualByComparingTo("0.00");
    }

    // --- registerSalePayment ---

    @Test
    void salePaymentGreaterThanNoteBalanceIsRejected() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder noteA = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        when(deliveryOrderRepository.findCustomerIdBySaleId(1L)).thenReturn(Optional.of(CUSTOMER_ID));
        when(deliveryOrderRepository.findWithSaleBySaleId(1L)).thenReturn(Optional.of(noteA));
        pendingNotes(customer, noteA);
        paidAmounts(paid(1L, "300.00"));

        assertThatThrownBy(() -> service.registerSalePayment(1L, payment("700.01", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mayor que el saldo");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void salePaymentThatClearsTheBalanceSettlesTheNote() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder noteA = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        when(deliveryOrderRepository.findCustomerIdBySaleId(1L)).thenReturn(Optional.of(CUSTOMER_ID));
        when(deliveryOrderRepository.findWithSaleBySaleId(1L)).thenReturn(Optional.of(noteA));
        pendingNotes(customer, noteA);
        paidAmounts(paid(1L, "300.00"));

        PaymentResultDto result = service.registerSalePayment(1L, payment("700", null));

        assertThat(savedPayments).singleElement().satisfies(payment ->
                assertThat(payment.getNotes()).isEqualTo("Abono a la nota #1"));
        assertThat(noteA.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(noteA.getSale().getPaid()).isTrue();
        assertThat(result.getSaleBalance()).isEqualByComparingTo("0.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("0.00");
    }

    // --- assertCreditAllowsNewOrder (R12) ---

    @Test
    void newOrderIsRejectedForNonAdminWhenDebtReachesLimit() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "500");
        pendingNotes(customer, order(101L, 1L, customer, "500.00", DeliveryStatus.PENDIENTE, 0));

        assertThatThrownBy(() -> service.assertCreditAllowsNewOrder(CUSTOMER_ID, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("$500.00")
                .hasMessageContaining("administrador");
    }

    @Test
    void newOrderIsAllowedForAdminWhenDebtReachesLimit() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "500");
        pendingNotes(customer, order(101L, 1L, customer, "800.00", DeliveryStatus.PENDIENTE, 0));

        assertThatCode(() -> service.assertCreditAllowsNewOrder(CUSTOMER_ID, true)).doesNotThrowAnyException();
    }

    @Test
    void customerWithoutDebtIsNeverBlockedEvenWithZeroLimit() {
        lockedCustomer(CUSTOMER_ID, "0");

        assertThatCode(() -> service.assertCreditAllowsNewOrder(CUSTOMER_ID, false)).doesNotThrowAnyException();
    }
}
