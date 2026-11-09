package com.pasadita.api.services.account;

import com.pasadita.api.config.SalesProperties;
import com.pasadita.api.dto.account.AppliedPaymentDto;
import com.pasadita.api.dto.account.CustomerAccountMapper;
import com.pasadita.api.dto.account.CustomerAccountResponseDto;
import com.pasadita.api.dto.account.DeliveryCollectionDto;
import com.pasadita.api.dto.account.PaymentActor;
import com.pasadita.api.dto.account.PaymentCancelDto;
import com.pasadita.api.dto.account.PaymentCreateDto;
import com.pasadita.api.dto.account.PaymentDetailDto;
import com.pasadita.api.dto.account.PaymentResultDto;
import com.pasadita.api.dto.account.PaymentUpdateDto;
import com.pasadita.api.dto.account.SalePaidAmount;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.PaymentMethod;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.entities.SalePayment;
import com.pasadita.api.entities.SalePaymentHistory;
import com.pasadita.api.enums.account.PaymentHistoryAction;
import com.pasadita.api.enums.account.PaymentStatus;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.enums.invoice.InvoiceStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.InvoiceRepository;
import com.pasadita.api.repositories.PaymentMethodRepository;
import com.pasadita.api.repositories.SalePaymentHistoryRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceImplTest {

    private static final long WALK_IN_CUSTOMER_ID = 1L;
    private static final long CUSTOMER_ID = 20L;
    private static final long EMPLOYEE_ID = 5L;
    private static final long OTHER_EMPLOYEE_ID = 6L;
    private static final long ADMIN_ID = 9L;
    private static final long CASH_ID = 1L;
    private static final long CARD_ID = 4L;
    private static final LocalDateTime FIRST_DAY = LocalDateTime.of(2026, 10, 1, 15, 0);
    // 12:00 in Mexico City (UTC-6) on 2026-10-27
    private static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 10, 27, 18, 0);

    // The employee always comes from the authenticated user, never from the body
    private static final PaymentActor CASHIER = new PaymentActor("cajero1", false);
    private static final PaymentActor OTHER_CASHIER = new PaymentActor("cajero2", false);
    private static final PaymentActor ADMIN = new PaymentActor("admin", true);

    @Mock private CustomerRepository customerRepository;
    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private SalePaymentRepository salePaymentRepository;
    @Mock private SalePaymentHistoryRepository salePaymentHistoryRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private InvoiceRepository invoiceRepository;

    private CustomerAccountServiceImpl service;
    private final List<SalePayment> savedPayments = new ArrayList<>();
    private final List<SalePaymentHistory> savedHistory = new ArrayList<>();
    private final Employee cashier = Employee.builder().id(EMPLOYEE_ID).fullName("Repartidor").username("cajero1").build();
    private final Employee otherCashier = Employee.builder().id(OTHER_EMPLOYEE_ID).fullName("Otra cajera")
            .username("cajero2").build();
    private final Employee admin = Employee.builder().id(ADMIN_ID).fullName("Administrador").username("admin").build();
    private final PaymentMethod cash = PaymentMethod.builder().id(CASH_ID).name("Efectivo").build();
    private final PaymentMethod card = PaymentMethod.builder().id(CARD_ID).name("Tarjeta").build();
    private long nextPaymentId = 1000L;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW_UTC.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new CustomerAccountServiceImpl(customerRepository, deliveryOrderRepository, salePaymentRepository,
                salePaymentHistoryRepository, employeeRepository, paymentMethodRepository, invoiceRepository,
                new CustomerAccountMapper(), new PaymentEditPolicy(clock), new SalesProperties(WALK_IN_CUSTOMER_ID));

        for (Employee employee : List.of(cashier, otherCashier, admin)) {
            lenient().when(employeeRepository.findByUsername(employee.getUsername())).thenReturn(Optional.of(employee));
        }
        lenient().when(paymentMethodRepository.findById(CASH_ID)).thenReturn(Optional.of(cash));
        lenient().when(paymentMethodRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        // Saving assigns an id the first time, like the IDENTITY column; saving an existing payment again updates it
        lenient().when(salePaymentRepository.save(any(SalePayment.class))).thenAnswer(invocation -> {
            SalePayment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(nextPaymentId++);
            }
            if (savedPayments.stream().noneMatch(saved -> saved == payment)) {
                savedPayments.add(payment);
            }
            return payment;
        });
        lenient().when(salePaymentHistoryRepository.save(any(SalePaymentHistory.class))).thenAnswer(invocation -> {
            SalePaymentHistory history = invocation.getArgument(0);
            savedHistory.add(history);
            return history;
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
        when(salePaymentRepository.sumActiveAmountsBySaleIds(anyCollection())).thenReturn(Arrays.asList(paidAmounts));
    }

    private static SalePaidAmount paid(long saleId, String amount) {
        return new SalePaidAmount(saleId, new BigDecimal(amount));
    }

    // Payment lookups read back what the service saved, the way later requests would see it in the database
    private void storedPaymentsAreVisible() {
        lenient().when(salePaymentRepository.existsBySaleIdAndStatus(anyLong(), any(PaymentStatus.class)))
                .thenAnswer(invocation -> savedPayments.stream()
                        .anyMatch(payment -> payment.getSale().getId().equals(invocation.getArgument(0))
                                && payment.getStatus() == invocation.getArgument(1)));
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
                .amountReceived(new BigDecimal(amountReceived))
                .paymentMethodId(CASH_ID)
                .clientRequestId(clientRequestId)
                .build();
    }

    private static PaymentCreateDto payment(String amount, String clientRequestId) {
        return PaymentCreateDto.builder()
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

        PaymentResultDto result = service.registerDeliveryCollection(103L, collection("500"), CASHIER);

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
        assertThat(savedHistory).satisfiesExactly(
                notePayment -> assertCreated(notePayment, savedPayments.get(0), "390.00", cashier),
                surplusPayment -> assertCreated(surplusPayment, savedPayments.get(1), "110.00", cashier));
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("1267.00");
        assertThat(result.getChange()).isEqualByComparingTo("0.00");
        assertThat(result.getOrderStatus()).isEqualTo(DeliveryStatus.ACTIVO);
    }

    @Test
    void shortfallLeavesOrderPendingWithPaymentForWhatWasReceived() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "1000");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        PaymentResultDto result = service.registerDeliveryCollection(200L, collection("400"), CASHIER);

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
                .amountReceived(BigDecimal.ZERO)
                .build();
        PaymentResultDto result = service.registerDeliveryCollection(200L, nothingReceived, CASHIER);

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

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("700.00"), CASHIER))
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

        PaymentResultDto result = service.registerDeliveryCollection(103L, collection("700"), CASHIER);

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

        PaymentResultDto result = service.registerDeliveryCollection(200L, collection("500", "req-1"), CASHIER);
        PaymentResultDto retry = service.registerDeliveryCollection(200L, collection("500", "req-1"), CASHIER);

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

        service.registerDeliveryCollection(103L, collection("500", "req-1"), CASHIER);

        assertThatThrownBy(() -> service.registerDeliveryCollection(103L, collection("500", "req-2"), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene abonos");
        assertThat(savedPayments).extracting(payment -> payment.getSale().getId()).containsExactly(3L, 1L);
    }

    @Test
    void walkInCustomerCannotBeLeftWithBalance() {
        Customer walkIn = lockedCustomer(WALK_IN_CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, walkIn, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("400"), CASHIER))
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

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("400"), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no está ACTIVO");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void collectionIsRejectedWhenNoteAlreadyHasPayments() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);
        when(salePaymentRepository.existsBySaleIdAndStatus(50L, PaymentStatus.APLICADO)).thenReturn(true);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("400"), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene abonos");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void shortfallOverCreditLimitIsStillRecordedAndFlagged() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "200");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        PaymentResultDto result = service.registerDeliveryCollection(200L, collection("400"), CASHIER);

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

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("700.00"), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay nada que registrar");
    }

    // --- registerAccountPayment ---

    @Test
    void storePaymentIsRejectedWhenCustomerHasNoDebt() {
        lockedCustomer(CUSTOMER_ID, "0");

        assertThatThrownBy(() -> service.registerAccountPayment(CUSTOMER_ID, payment("100", null), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no tiene deuda");
        assertThat(savedPayments).isEmpty();
    }

    @Test
    void storePaymentGoesToOldestNoteAndRestIsChange() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder oldest = order(101L, 1L, customer, "100.00", DeliveryStatus.PENDIENTE, 0);
        pendingNotes(customer, oldest);

        PaymentResultDto result = service.registerAccountPayment(CUSTOMER_ID, payment("120", "req-9"), CASHIER);

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

        PaymentResultDto result = service.registerAccountPayment(CUSTOMER_ID, payment("200", "req-1"), CASHIER);

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

        assertThatThrownBy(() -> service.registerSalePayment(1L, payment("700.01", null), ADMIN))
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

        PaymentResultDto result = service.registerSalePayment(1L, payment("700", null), ADMIN);

        assertThat(savedPayments).singleElement().satisfies(payment ->
                assertThat(payment.getNotes()).isEqualTo("Abono a la nota #1"));
        assertThat(savedHistory).singleElement()
                .satisfies(history -> assertCreated(history, savedPayments.get(0), "700.00", admin));
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

    // --- in-memory account: lookups read the orders and payments the way the database would after each operation ---

    // Cancelled payments are left out of the sums and the "has payments" checks, like the status-filtered queries
    private void inMemoryAccount(Customer customer, DeliveryOrder... oldestFirst) {
        List<DeliveryOrder> orders = Arrays.asList(oldestFirst);
        lenient().when(deliveryOrderRepository.findByCustomerIdAndStatusOldestFirst(eq(customer.getId()),
                any(DeliveryStatus.class))).thenAnswer(invocation -> orders.stream()
                .filter(order -> order.getStatus() == invocation.getArgument(1))
                .toList());
        for (DeliveryOrder order : orders) {
            Long saleId = order.getSale().getId();
            lenient().when(deliveryOrderRepository.findWithSaleBySaleId(saleId)).thenReturn(Optional.of(order));
            lenient().when(deliveryOrderRepository.findCustomerIdBySaleId(saleId)).thenReturn(Optional.of(customer.getId()));
            lenient().when(deliveryOrderRepository.existsBySaleId(saleId)).thenReturn(true);
        }
        lenient().when(salePaymentRepository.sumActiveAmountsBySaleIds(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> saleIds = invocation.getArgument(0);
            return savedPayments.stream()
                    .filter(payment -> payment.getStatus() == PaymentStatus.APLICADO
                            && saleIds.contains(payment.getSale().getId()))
                    .collect(Collectors.groupingBy(payment -> payment.getSale().getId(),
                            Collectors.reducing(BigDecimal.ZERO, SalePayment::getAmount, BigDecimal::add)))
                    .entrySet().stream()
                    .map(entry -> new SalePaidAmount(entry.getKey(), entry.getValue()))
                    .toList();
        });
        lenient().when(salePaymentRepository.findCustomerIdById(anyLong())).thenAnswer(invocation ->
                storedPayment(invocation.getArgument(0)).map(payment -> customer.getId()));
        lenient().when(salePaymentRepository.findWithSaleById(anyLong())).thenAnswer(invocation ->
                storedPayment(invocation.getArgument(0)));
        lenient().when(salePaymentRepository.findBySaleIdOrderByDatetimeAscIdAsc(anyLong())).thenAnswer(invocation ->
                savedPayments.stream()
                        .filter(payment -> payment.getSale().getId().equals(invocation.getArgument(0)))
                        .toList());
        lenient().when(salePaymentRepository.findBySaleIdInAndStatusOrderByDatetimeAscIdAsc(anyCollection(),
                any(PaymentStatus.class))).thenAnswer(invocation -> {
            Collection<Long> saleIds = invocation.getArgument(0);
            return savedPayments.stream()
                    .filter(payment -> saleIds.contains(payment.getSale().getId())
                            && payment.getStatus() == invocation.getArgument(1))
                    .toList();
        });
        storedPaymentsAreVisible();
    }

    private Optional<SalePayment> storedPayment(Long paymentId) {
        return savedPayments.stream()
                .filter(payment -> paymentId.equals(payment.getId()))
                .findFirst();
    }

    // A payment already in the database (no CREADO row written by this test)
    private SalePayment existingPayment(DeliveryOrder deliveryOrder, Employee employee, String amount,
                                        LocalDateTime datetimeUtc) {
        SalePayment payment = SalePayment.builder()
                .id(nextPaymentId++)
                .sale(deliveryOrder.getSale())
                .employee(employee)
                .paymentMethod(cash)
                .amount(new BigDecimal(amount))
                .datetime(datetimeUtc)
                .build();
        savedPayments.add(payment);
        return payment;
    }

    private static PaymentUpdateDto change(String amount, long paymentMethodId) {
        return PaymentUpdateDto.builder()
                .amount(new BigDecimal(amount))
                .paymentMethodId(paymentMethodId)
                .reason("Capturé mal el monto")
                .build();
    }

    private static PaymentCancelDto cancellation() {
        return PaymentCancelDto.builder().reason("Cobro duplicado").build();
    }

    private static void assertCreated(SalePaymentHistory history, SalePayment payment, String amount, Employee author) {
        assertThat(history.getAction()).isEqualTo(PaymentHistoryAction.CREADO);
        assertThat(history.getPayment()).isSameAs(payment);
        assertThat(history.getPreviousAmount()).isNull();
        assertThat(history.getPreviousPaymentMethod()).isNull();
        assertThat(history.getNewAmount()).isEqualByComparingTo(amount);
        assertThat(history.getNewPaymentMethod()).isSameAs(payment.getPaymentMethod());
        assertThat(history.getChangedBy()).isSameAs(author);
    }

    // --- authenticated employee and stamped invoices on collections ---

    @Test
    void recordedEmployeeAndHistoryAuthorComeFromTheAuthenticatedUser() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "1000");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);

        service.registerDeliveryCollection(200L, collection("400"), OTHER_CASHIER);

        assertThat(savedPayments).singleElement()
                .satisfies(payment -> assertThat(payment.getEmployee()).isSameAs(otherCashier));
        assertThat(savedHistory).singleElement()
                .satisfies(history -> assertCreated(history, savedPayments.get(0), "400.00", otherCashier));
        verify(employeeRepository).findByUsername("cajero2");
    }

    @Test
    void shortfallIsRejectedWhenTheNoteHasAStampedInvoice() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "1000");
        DeliveryOrder deliveryOrder = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        beingCollected(deliveryOrder);
        when(invoiceRepository.existsBySaleIdAndStatus(50L, InvoiceStatus.TIMBRADA)).thenReturn(true);

        assertThatThrownBy(() -> service.registerDeliveryCollection(200L, collection("400"), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cancela primero la factura");
        assertThat(deliveryOrder.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(savedPayments).isEmpty();
    }

    // --- modifyPayment ---

    @Test
    void raisingAPaymentUntilTheBalanceIsClearedSettlesTheNote() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "400.00", NOW_UTC.minusHours(2));

        PaymentResultDto result = service.modifyPayment(payment.getId(), change("700", CASH_ID), CASHIER);

        assertThat(payment.getAmount()).isEqualByComparingTo("700.00");
        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(note.getSale().getPaid()).isTrue();
        assertThat(result.getOrderStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("0.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("0.00");
        assertThat(result.getChange()).isEqualByComparingTo("0.00");
        assertThat(result.getAppliedPayments()).singleElement().satisfies(applied -> {
            assertThat(applied.getSaleId()).isEqualTo(50L);
            assertThat(applied.getAmount()).isEqualByComparingTo("700.00");
            assertThat(applied.getRemainingBalance()).isEqualByComparingTo("0.00");
        });
        assertThat(savedHistory).singleElement().satisfies(history -> {
            assertThat(history.getAction()).isEqualTo(PaymentHistoryAction.MODIFICADO);
            assertThat(history.getPayment()).isSameAs(payment);
            assertThat(history.getPreviousAmount()).isEqualByComparingTo("400.00");
            assertThat(history.getNewAmount()).isEqualByComparingTo("700.00");
            assertThat(history.getPreviousPaymentMethod()).isSameAs(cash);
            assertThat(history.getNewPaymentMethod()).isSameAs(cash);
            assertThat(history.getReason()).isEqualTo("Capturé mal el monto");
            assertThat(history.getChangedBy()).isSameAs(cashier);
        });
    }

    @Test
    void loweringAPaymentOfASettledNoteLeavesItPendingAndFlagsTheCreditLimit() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "100");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "700.00", FIRST_DAY);

        PaymentResultDto result = service.modifyPayment(payment.getId(), change("400", CARD_ID), ADMIN);

        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(note.getSale().getPaid()).isFalse();
        assertThat(payment.getPaymentMethod()).isSameAs(card);
        assertThat(result.getOrderStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("300.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("300.00");
        assertThat(result.getCreditLimit()).isEqualByComparingTo("100.00");
        assertThat(result.isOverLimit()).isTrue();
        assertThat(savedHistory).singleElement().satisfies(history -> {
            assertThat(history.getPreviousPaymentMethod()).isSameAs(cash);
            assertThat(history.getNewPaymentMethod()).isSameAs(card);
            assertThat(history.getChangedBy()).isSameAs(admin);
        });
    }

    @Test
    void amountAboveWhatTheNoteOwesWithoutThisPaymentIsRejected() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "300.00", NOW_UTC);
        existingPayment(note, otherCashier, "200.00", NOW_UTC);

        assertThatThrownBy(() -> service.modifyPayment(payment.getId(), change("500.01", CASH_ID), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("$500.00")
                .hasMessageContaining("crea un abono nuevo");
        assertThat(payment.getAmount()).isEqualByComparingTo("300.00");
        assertThat(savedHistory).isEmpty();

        PaymentResultDto result = service.modifyPayment(payment.getId(), change("500", CASH_ID), CASHIER);

        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroAmountIsRejectedBecauseThatIsACancellation() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "300.00", NOW_UTC);

        assertThatThrownBy(() -> service.modifyPayment(payment.getId(), change("0", CASH_ID), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mayor a 0");
        assertThat(savedHistory).isEmpty();
    }

    @Test
    void nonAdminCannotModifyAnotherEmployeesPayment() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "300.00", NOW_UTC);

        assertThatThrownBy(() -> service.modifyPayment(payment.getId(), change("350", CASH_ID), OTHER_CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Solo puedes modificar tus abonos del día. Pide al administrador que lo corrija.");
        assertThat(payment.getAmount()).isEqualByComparingTo("300.00");
        assertThat(savedHistory).isEmpty();
    }

    @Test
    void nonAdminCannotModifyTheirOwnPaymentFromAnotherMexicoDay() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment yesterday = existingPayment(note, cashier, "300.00", NOW_UTC.minusDays(1));
        // 05:30 UTC on the 27th is still 23:30 of the 26th in Mexico
        SalePayment lastNightInMexico = existingPayment(note, cashier, "100.00", LocalDateTime.of(2026, 10, 27, 5, 30));

        assertThatThrownBy(() -> service.modifyPayment(yesterday.getId(), change("350", CASH_ID), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Solo puedes modificar tus abonos del día");
        assertThatThrownBy(() -> service.modifyPayment(lastNightInMexico.getId(), change("150", CASH_ID), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Solo puedes modificar tus abonos del día");
        assertThat(savedHistory).isEmpty();
    }

    @Test
    void nonAdminCanModifyTheirOwnPaymentOfTheSameMexicoDay() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        // 06:30 UTC on the 27th is 00:30 of the 27th in Mexico
        SalePayment payment = existingPayment(note, cashier, "300.00", LocalDateTime.of(2026, 10, 27, 6, 30));

        PaymentResultDto result = service.modifyPayment(payment.getId(), change("350", CASH_ID), CASHIER);

        assertThat(payment.getAmount()).isEqualByComparingTo("350.00");
        assertThat(result.getSaleBalance()).isEqualByComparingTo("350.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("350.00");
        assertThat(savedHistory).singleElement()
                .satisfies(history -> assertThat(history.getChangedBy()).isSameAs(cashier));
    }

    @Test
    void cancelledPaymentCannotBeModified() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "300.00", NOW_UTC);
        payment.setStatus(PaymentStatus.CANCELADO);

        assertThatThrownBy(() -> service.modifyPayment(payment.getId(), change("350", CASH_ID), ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("está cancelado");
        assertThat(savedHistory).isEmpty();
    }

    @Test
    void modificationWithoutRealChangesWritesNothing() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "400.00", NOW_UTC);

        PaymentResultDto result = service.modifyPayment(payment.getId(), change("400", CASH_ID), CASHIER);

        verify(salePaymentRepository, never()).save(any());
        assertThat(savedHistory).isEmpty();
        assertThat(result.getOrderStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("300.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("300.00");
    }

    @Test
    void paymentsOfANoteWithAStampedInvoiceCannotBeModifiedOrCancelled() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.ACTIVO, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "700.00", NOW_UTC);
        when(invoiceRepository.existsBySaleIdAndStatus(50L, InvoiceStatus.TIMBRADA)).thenReturn(true);

        assertThatThrownBy(() -> service.modifyPayment(payment.getId(), change("400", CASH_ID), ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cancela primero la factura");
        assertThatThrownBy(() -> service.cancelPayment(payment.getId(), cancellation(), ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cancela primero la factura");
        assertThat(payment.getAmount()).isEqualByComparingTo("700.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APLICADO);
        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(savedHistory).isEmpty();
    }

    @Test
    void walkInPaymentCannotBeLoweredButItsMethodCanChange() {
        Customer walkIn = lockedCustomer(WALK_IN_CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, walkIn, "390.00", DeliveryStatus.ACTIVO, 0);
        inMemoryAccount(walkIn, note);
        SalePayment payment = existingPayment(note, cashier, "390.00", NOW_UTC);

        assertThatThrownBy(() -> service.modifyPayment(payment.getId(), change("300", CASH_ID), ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Público en general");
        PaymentResultDto result = service.modifyPayment(payment.getId(), change("390", CARD_ID), ADMIN);

        assertThat(payment.getPaymentMethod()).isSameAs(card);
        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("0.00");
        assertThat(savedHistory).singleElement()
                .satisfies(history -> assertThat(history.getAction()).isEqualTo(PaymentHistoryAction.MODIFICADO));
    }

    // --- cancelPayment ---

    @Test
    void cancellingTwiceIsIdempotentAndWritesASingleHistoryRow() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "1000");
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment payment = existingPayment(note, cashier, "300.00", FIRST_DAY);

        PaymentResultDto first = service.cancelPayment(payment.getId(), cancellation(), ADMIN);
        PaymentResultDto second = service.cancelPayment(payment.getId(), cancellation(), ADMIN);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELADO);
        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(first.getSaleBalance()).isEqualByComparingTo("700.00");
        assertThat(first.getCustomerDebt()).isEqualByComparingTo("700.00");
        assertThat(first.getAppliedPayments()).isEmpty();
        assertThat(second).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(first);
        verify(salePaymentRepository, times(1)).save(payment);
        assertThat(savedHistory).singleElement().satisfies(history -> {
            assertThat(history.getAction()).isEqualTo(PaymentHistoryAction.CANCELADO);
            assertThat(history.getPreviousAmount()).isEqualByComparingTo("300.00");
            assertThat(history.getPreviousPaymentMethod()).isSameAs(cash);
            assertThat(history.getNewAmount()).isNull();
            assertThat(history.getNewPaymentMethod()).isNull();
            assertThat(history.getReason()).isEqualTo("Cobro duplicado");
            assertThat(history.getChangedBy()).isSameAs(admin);
        });
    }

    @Test
    void cancellingTheNoteRowOfASurplusCollectionLeavesItPendingAndASecondCollectionIsStillRejected() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder oldNote = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        DeliveryOrder collected = order(103L, 3L, customer, "390.00", DeliveryStatus.ACTIVO, 2);
        inMemoryAccount(customer, oldNote, collected);
        beingCollected(collected);

        service.registerDeliveryCollection(103L, collection("500", "req-1"), CASHIER);
        SalePayment noteRow = savedPayments.get(0);
        PaymentResultDto result = service.cancelPayment(noteRow.getId(), cancellation(), ADMIN);

        assertThat(collected.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(collected.getSale().getPaid()).isFalse();
        assertThat(result.getSaleBalance()).isEqualByComparingTo("390.00");
        assertThat(result.getCustomerDebt()).isEqualByComparingTo("1280.00");
        assertThatThrownBy(() -> service.registerDeliveryCollection(103L, collection("500", "req-2"), CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no está ACTIVO");
        assertThat(savedHistory).extracting(SalePaymentHistory::getAction).containsExactly(
                PaymentHistoryAction.CREADO, PaymentHistoryAction.CREADO, PaymentHistoryAction.CANCELADO);
    }

    @Test
    void cancellingAWalkInPaymentIsAllowedSoItsOrderCanBeCancelledNext() {
        Customer walkIn = lockedCustomer(WALK_IN_CUSTOMER_ID, "0");
        DeliveryOrder note = order(200L, 50L, walkIn, "390.00", DeliveryStatus.ACTIVO, 0);
        inMemoryAccount(walkIn, note);
        SalePayment payment = existingPayment(note, cashier, "390.00", NOW_UTC);

        PaymentResultDto result = service.cancelPayment(payment.getId(), cancellation(), ADMIN);

        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("390.00");
    }

    @Test
    void retryOfARequestWhosePaymentWasCancelledAppliesNothing() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);

        service.registerAccountPayment(CUSTOMER_ID, payment("300", "req-1"), CASHIER);
        service.cancelPayment(savedPayments.get(0).getId(), cancellation(), ADMIN);
        PaymentResultDto retry = service.registerAccountPayment(CUSTOMER_ID, payment("300", "req-1"), CASHIER);

        assertThat(savedPayments).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELADO));
        assertThat(savedHistory).extracting(SalePaymentHistory::getAction)
                .containsExactly(PaymentHistoryAction.CREADO, PaymentHistoryAction.CANCELADO);
        assertThat(retry.getCustomerDebt()).isEqualByComparingTo("1000.00");
    }

    // --- cancelled payments count for nothing (A2) ---

    @Test
    void cancelledPaymentsAreLeftOutOfTheDebtAndOfTheNotePaymentCap() {
        Customer customer = lockedCustomer(CUSTOMER_ID, "0");
        DeliveryOrder note = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        existingPayment(note, cashier, "300.00", FIRST_DAY);
        existingPayment(note, cashier, "200.00", FIRST_DAY).setStatus(PaymentStatus.CANCELADO);

        assertThat(service.getAccount(CUSTOMER_ID, ADMIN).getTotalDebt()).isEqualByComparingTo("700.00");
        assertThatThrownBy(() -> service.registerSalePayment(1L, payment("700.01", null), ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mayor que el saldo");
        PaymentResultDto result = service.registerSalePayment(1L, payment("700", null), ADMIN);

        assertThat(note.getStatus()).isEqualTo(DeliveryStatus.ACTIVO);
        assertThat(result.getSaleBalance()).isEqualByComparingTo("0.00");
    }

    // --- findSalePayments / getAccount: status, modified and editable ---

    @Test
    void notePaymentsIncludeCancelledOnesWithModifiedAndEditableForTheCaller() {
        Customer customer = Customer.builder().id(CUSTOMER_ID).name("Clienta").creditLimit(BigDecimal.ZERO).build();
        DeliveryOrder note = order(200L, 50L, customer, "700.00", DeliveryStatus.PENDIENTE, 0);
        inMemoryAccount(customer, note);
        SalePayment ownToday = existingPayment(note, cashier, "100.00", NOW_UTC);
        SalePayment ownYesterday = existingPayment(note, cashier, "100.00", NOW_UTC.minusDays(1));
        existingPayment(note, otherCashier, "100.00", NOW_UTC);
        existingPayment(note, cashier, "100.00", NOW_UTC).setStatus(PaymentStatus.CANCELADO);
        when(salePaymentHistoryRepository.findModifiedPaymentIds(anyCollection())).thenReturn(List.of(ownYesterday.getId()));

        List<PaymentDetailDto> forCashier = service.findSalePayments(50L, CASHIER);
        List<PaymentDetailDto> forAdmin = service.findSalePayments(50L, ADMIN);

        assertThat(forCashier).extracting(PaymentDetailDto::getStatus).containsExactly(PaymentStatus.APLICADO,
                PaymentStatus.APLICADO, PaymentStatus.APLICADO, PaymentStatus.CANCELADO);
        assertThat(forCashier).extracting(PaymentDetailDto::isModified).containsExactly(false, true, false, false);
        assertThat(forCashier).extracting(PaymentDetailDto::isEditable).containsExactly(true, false, false, false);
        assertThat(forAdmin).extracting(PaymentDetailDto::isEditable).containsExactly(true, true, true, false);
        assertThat(forCashier.get(0).getPaymentId()).isEqualTo(ownToday.getId());
    }

    @Test
    void accountStatementShowsOnlyActivePaymentsAndNothingIsEditableOnAStampedNote() {
        Customer customer = Customer.builder().id(CUSTOMER_ID).name("Clienta").creditLimit(BigDecimal.ZERO).build();
        DeliveryOrder stampedNote = order(101L, 1L, customer, "1000.00", DeliveryStatus.PENDIENTE, 0);
        DeliveryOrder plainNote = order(102L, 2L, customer, "500.00", DeliveryStatus.PENDIENTE, 1);
        inMemoryAccount(customer, stampedNote, plainNote);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        SalePayment onStamped = existingPayment(stampedNote, cashier, "100.00", NOW_UTC);
        existingPayment(stampedNote, cashier, "50.00", NOW_UTC).setStatus(PaymentStatus.CANCELADO);
        SalePayment onPlain = existingPayment(plainNote, cashier, "200.00", NOW_UTC);
        when(invoiceRepository.findSaleIdsByStatus(anyCollection(), eq(InvoiceStatus.TIMBRADA))).thenReturn(List.of(1L));

        CustomerAccountResponseDto account = service.getAccount(CUSTOMER_ID, CASHIER);

        assertThat(account.getTotalDebt()).isEqualByComparingTo("1200.00");
        assertThat(account.getPendingNotes()).satisfiesExactly(
                stamped -> assertThat(stamped.getPayments()).singleElement().satisfies(detail -> {
                    assertThat(detail.getPaymentId()).isEqualTo(onStamped.getId());
                    assertThat(detail.isEditable()).isFalse();
                }),
                plain -> assertThat(plain.getPayments()).singleElement().satisfies(detail -> {
                    assertThat(detail.getPaymentId()).isEqualTo(onPlain.getId());
                    assertThat(detail.getStatus()).isEqualTo(PaymentStatus.APLICADO);
                    assertThat(detail.isEditable()).isTrue();
                }));
    }
}
