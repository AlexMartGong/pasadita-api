package com.pasadita.api.services.account;

import com.pasadita.api.config.SalesProperties;
import com.pasadita.api.dto.account.AppliedPaymentDto;
import com.pasadita.api.dto.account.CustomerAccountMapper;
import com.pasadita.api.dto.account.CustomerAccountResponseDto;
import com.pasadita.api.dto.account.CustomerDebtSummaryDto;
import com.pasadita.api.dto.account.DeliveryCollectionDto;
import com.pasadita.api.dto.account.PaymentCreateDto;
import com.pasadita.api.dto.account.PaymentResultDto;
import com.pasadita.api.dto.account.PendingNoteDto;
import com.pasadita.api.dto.account.SalePaidAmount;
import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.PaymentMethod;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.entities.SalePayment;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.PaymentMethodRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Partial payments (abonos) and customer credit for delivery orders.
 * <p>
 * Every operation that applies payments or checks credit locks the customer row first, so two simultaneous
 * collections for the same customer can't apply money to the same note twice. The money-moving operations run in
 * READ_COMMITTED: under MySQL's default REPEATABLE READ, plain reads after the lock would keep using the snapshot of
 * the transaction's first read and miss payments committed by whoever held the lock. The {@code clientRequestId}
 * check therefore runs after the lock too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAccountServiceImpl implements CustomerAccountService {

    private final CustomerRepository customerRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final EmployeeRepository employeeRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final CustomerAccountMapper customerAccountMapper;
    private final SalesProperties salesProperties;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResultDto registerDeliveryCollection(Long orderId, DeliveryCollectionDto deliveryCollectionDto) {
        Long customerId = deliveryOrderRepository.findCustomerIdById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found with id: " + orderId));
        Customer customer = lockCustomer(customerId);
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findWithSaleById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found with id: " + orderId));
        Sale sale = deliveryOrder.getSale();
        BigDecimal total = toMoney(sale.getTotal());
        BigDecimal amountReceived = toMoney(deliveryCollectionDto.getAmountReceived());
        String clientRequestId = normalizeRequestId(deliveryCollectionDto.getClientRequestId());

        if (isAlreadyProcessed(clientRequestId)) {
            return currentResult(customer, deliveryOrder, clientRequestId, amountReceived);
        }
        if (deliveryOrder.getStatus() != DeliveryStatus.ACTIVO) {
            throw new BusinessRuleException("El pedido #" + orderId + " no está ACTIVO (estado actual: "
                    + deliveryOrder.getStatus() + ")");
        }
        if (salePaymentRepository.existsBySaleId(sale.getId())) {
            throw new BusinessRuleException("La nota #" + sale.getId() + " del pedido #" + orderId
                    + " ya tiene abonos registrados");
        }

        int comparison = amountReceived.compareTo(total);
        if (comparison == 0) {
            throw new BusinessRuleException("No hay nada que registrar: el cliente pagó exactamente el total del pedido #"
                    + orderId);
        }
        if (comparison < 0 && isWalkInCustomer(customer)) {
            throw new BusinessRuleException("Público en general no puede quedar con saldo: cobra el total del pedido ("
                    + formatMoney(total) + ")");
        }

        Employee employee = findEmployeeById(deliveryCollectionDto.getEmployeeId());
        PaymentMethod paymentMethod = amountReceived.signum() > 0
                ? findPaymentMethodById(deliveryCollectionDto.getPaymentMethodId())
                : null;
        List<PendingNote> pendingNotes = loadPendingNotes(customerId);
        BigDecimal debtBefore = totalBalance(pendingNotes);

        // The note's own share (its whole total on a surplus) is stored first: a second collection for this order
        // then fails the "no payments" precondition instead of applying the surplus again
        BigDecimal paidOnNote = amountReceived.min(total);
        BigDecimal saleBalance = total.subtract(paidOnNote);
        List<AppliedPaymentDto> appliedPayments = new ArrayList<>();
        if (paidOnNote.signum() > 0) {
            salePaymentRepository.save(buildPayment(sale, employee, paymentMethod, paidOnNote,
                    "Cobro en entrega del pedido #" + orderId, clientRequestId));
            appliedPayments.add(new AppliedPaymentDto(sale.getId(), paidOnNote, saleBalance));
        }

        if (comparison < 0) {
            markAsPending(deliveryOrder);

            PaymentResultDto result = customerAccountMapper.toPaymentResultDto(deliveryOrder.getStatus(), saleBalance,
                    debtBefore.add(saleBalance), customer, BigDecimal.ZERO, appliedPayments);
            if (result.isOverLimit()) {
                log.warn("Cliente {} ({}) rebasó su límite de crédito con el pedido #{}: deuda {}, límite {}",
                        customer.getId(), customer.getName(), orderId, result.getCustomerDebt(), result.getCreditLimit());
            }
            return result;
        }

        NoteApplication application = applyToNotes(pendingNotes, amountReceived.subtract(total), employee,
                paymentMethod, "Excedente del pedido #" + orderId, clientRequestId);
        appliedPayments.addAll(application.appliedPayments());
        return customerAccountMapper.toPaymentResultDto(deliveryOrder.getStatus(), saleBalance,
                debtBefore.subtract(application.appliedTotal()), customer, application.change(), appliedPayments);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResultDto registerAccountPayment(Long customerId, PaymentCreateDto paymentCreateDto) {
        Customer customer = lockCustomer(customerId);
        BigDecimal amount = requirePositive(paymentCreateDto.getAmount());
        String clientRequestId = normalizeRequestId(paymentCreateDto.getClientRequestId());

        if (isAlreadyProcessed(clientRequestId)) {
            return currentResult(customer, null, clientRequestId, amount);
        }

        List<PendingNote> pendingNotes = loadPendingNotes(customerId);
        BigDecimal debtBefore = totalBalance(pendingNotes);
        if (debtBefore.signum() == 0) {
            throw new BusinessRuleException("El cliente " + customer.getName() + " no tiene deuda pendiente");
        }

        Employee employee = findEmployeeById(paymentCreateDto.getEmployeeId());
        PaymentMethod paymentMethod = findPaymentMethodById(paymentCreateDto.getPaymentMethodId());
        NoteApplication application = applyToNotes(pendingNotes, amount, employee, paymentMethod,
                notesOrDefault(paymentCreateDto.getNotes(), "Abono a cuenta"), clientRequestId);

        return customerAccountMapper.toPaymentResultDto(null, null,
                debtBefore.subtract(application.appliedTotal()), customer, application.change(),
                application.appliedPayments());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResultDto registerSalePayment(Long saleId, PaymentCreateDto paymentCreateDto) {
        Long customerId = deliveryOrderRepository.findCustomerIdBySaleId(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found for sale id: " + saleId));
        Customer customer = lockCustomer(customerId);
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findWithSaleBySaleId(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found for sale id: " + saleId));
        BigDecimal amount = requirePositive(paymentCreateDto.getAmount());
        String clientRequestId = normalizeRequestId(paymentCreateDto.getClientRequestId());

        if (isAlreadyProcessed(clientRequestId)) {
            return currentResult(customer, deliveryOrder, clientRequestId, amount);
        }
        if (deliveryOrder.getStatus() != DeliveryStatus.PENDIENTE) {
            throw new BusinessRuleException("La nota #" + saleId + " no está PENDIENTE (estado del pedido: "
                    + deliveryOrder.getStatus() + ")");
        }

        List<PendingNote> pendingNotes = loadPendingNotes(customerId);
        PendingNote note = pendingNotes.stream()
                .filter(pendingNote -> pendingNote.saleId().equals(saleId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("La nota #" + saleId + " no está PENDIENTE"));
        if (amount.compareTo(note.balance()) > 0) {
            throw new BusinessRuleException("El abono (" + formatMoney(amount) + ") es mayor que el saldo de la nota #"
                    + saleId + " (" + formatMoney(note.balance()) + ")");
        }

        Employee employee = findEmployeeById(paymentCreateDto.getEmployeeId());
        PaymentMethod paymentMethod = findPaymentMethodById(paymentCreateDto.getPaymentMethodId());
        NoteApplication application = applyToNotes(List.of(note), amount, employee, paymentMethod,
                notesOrDefault(paymentCreateDto.getNotes(), "Abono a la nota #" + saleId), clientRequestId);

        return customerAccountMapper.toPaymentResultDto(note.deliveryOrder().getStatus(),
                note.balance().subtract(amount), totalBalance(pendingNotes).subtract(amount), customer,
                application.change(), application.appliedPayments());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerAccountResponseDto getAccount(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + customerId));
        return buildAccount(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDebtSummaryDto> findCustomersWithDebt() {
        return customerRepository.findCustomersWithDebt().stream()
                .map(customerAccountMapper::toDebtSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CustomerAccountResponseDto updateCreditLimit(Long customerId, BigDecimal creditLimit) {
        if (creditLimit == null || creditLimit.signum() < 0) {
            throw new BusinessRuleException("El límite de crédito debe ser mayor o igual a 0");
        }
        Customer customer = lockCustomer(customerId);
        customer.setCreditLimit(toMoney(creditLimit));
        customerRepository.save(customer);
        log.info("Límite de crédito del cliente {} actualizado a {}", customerId, customer.getCreditLimit());
        return buildAccount(customer);
    }

    @Override
    @Transactional
    public void assertCreditAllowsNewOrder(Long customerId, boolean requestedByAdmin) {
        Customer customer = lockCustomer(customerId);
        BigDecimal debt = totalBalance(loadPendingNotes(customerId));
        BigDecimal creditLimit = customer.getCreditLimit();

        boolean creditExhausted = debt.signum() > 0 && debt.compareTo(creditLimit) >= 0;
        if (!creditExhausted) {
            return;
        }
        if (!requestedByAdmin) {
            throw new BusinessRuleException("El cliente " + customer.getName() + " tiene una deuda de "
                    + formatMoney(debt) + " y su límite de crédito es de " + formatMoney(creditLimit)
                    + ". Se requiere autorización del administrador para crear el pedido.");
        }
        log.info("Pedido autorizado por ADMIN para el cliente {} con deuda {} y límite {}",
                customerId, debt, creditLimit);
    }

    // Applies the amount to the notes in the given order (oldest first); whatever is left is change for the customer
    private NoteApplication applyToNotes(List<PendingNote> notes, BigDecimal amount, Employee employee,
                                         PaymentMethod paymentMethod, String paymentNotes, String clientRequestId) {
        BigDecimal remaining = amount;
        List<AppliedPaymentDto> appliedPayments = new ArrayList<>();

        for (PendingNote note : notes) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal balance = note.balance();
            if (balance.signum() <= 0) {
                continue;
            }
            BigDecimal appliedAmount = balance.min(remaining);
            salePaymentRepository.save(buildPayment(note.deliveryOrder().getSale(), employee, paymentMethod,
                    appliedAmount, paymentNotes, clientRequestId));
            remaining = remaining.subtract(appliedAmount);

            BigDecimal remainingBalance = balance.subtract(appliedAmount);
            if (remainingBalance.signum() == 0) {
                markAsSettled(note.deliveryOrder());
            }
            appliedPayments.add(new AppliedPaymentDto(note.saleId(), appliedAmount, remainingBalance));
        }

        return new NoteApplication(appliedPayments, amount.subtract(remaining), remaining);
    }

    // Idempotent replay: the current state plus the payments already stored under this clientRequestId; the change is
    // whatever the request brought that those payments didn't absorb
    private PaymentResultDto currentResult(Customer customer, DeliveryOrder deliveryOrder, String clientRequestId,
                                           BigDecimal requestAmount) {
        log.info("Solicitud {} ya registrada: se devuelve el estado actual sin duplicar abonos", clientRequestId);
        List<PendingNote> pendingNotes = loadPendingNotes(customer.getId());
        Map<Long, BigDecimal> balanceBySaleId = pendingNotes.stream()
                .collect(Collectors.toMap(PendingNote::saleId, PendingNote::balance));

        List<SalePayment> previousPayments = salePaymentRepository.findByClientRequestIdOrderByIdAsc(clientRequestId);
        List<AppliedPaymentDto> appliedPayments = previousPayments.stream()
                .map(payment -> new AppliedPaymentDto(payment.getSale().getId(), payment.getAmount(),
                        balanceBySaleId.getOrDefault(payment.getSale().getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
        BigDecimal appliedTotal = previousPayments.stream()
                .map(SalePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DeliveryStatus orderStatus = deliveryOrder != null ? deliveryOrder.getStatus() : null;
        BigDecimal saleBalance = deliveryOrder != null
                ? balanceBySaleId.getOrDefault(deliveryOrder.getSale().getId(), BigDecimal.ZERO)
                : null;
        return customerAccountMapper.toPaymentResultDto(orderStatus, saleBalance, totalBalance(pendingNotes), customer,
                requestAmount.subtract(appliedTotal).max(BigDecimal.ZERO), appliedPayments);
    }

    private CustomerAccountResponseDto buildAccount(Customer customer) {
        List<PendingNote> pendingNotes = loadPendingNotes(customer.getId());
        Map<Long, List<SalePayment>> paymentsBySaleId = pendingNotes.isEmpty()
                ? Map.of()
                : salePaymentRepository.findBySaleIdInOrderByDatetimeAscIdAsc(saleIds(pendingNotes)).stream()
                .collect(Collectors.groupingBy(payment -> payment.getSale().getId()));

        List<PendingNoteDto> pendingNoteDtos = pendingNotes.stream()
                .map(note -> customerAccountMapper.toPendingNoteDto(note.deliveryOrder(), note.paidAmount(),
                        note.balance(), paymentsBySaleId.getOrDefault(note.saleId(), List.of())))
                .collect(Collectors.toList());
        return customerAccountMapper.toAccountResponseDto(customer, totalBalance(pendingNotes), pendingNoteDtos);
    }

    // Customer debt (R10): notes whose order is PENDIENTE, oldest first, each with its total minus its payments
    private List<PendingNote> loadPendingNotes(Long customerId) {
        List<DeliveryOrder> pendingOrders = deliveryOrderRepository
                .findByCustomerIdAndStatusOldestFirst(customerId, DeliveryStatus.PENDIENTE);
        if (pendingOrders.isEmpty()) {
            return List.of();
        }

        List<Long> saleIds = pendingOrders.stream()
                .map(order -> order.getSale().getId())
                .collect(Collectors.toList());
        Map<Long, BigDecimal> paidBySaleId = salePaymentRepository.sumAmountsBySaleIds(saleIds).stream()
                .collect(Collectors.toMap(SalePaidAmount::saleId, SalePaidAmount::paidAmount));

        return pendingOrders.stream()
                .map(order -> new PendingNote(order,
                        toMoney(paidBySaleId.getOrDefault(order.getSale().getId(), BigDecimal.ZERO))))
                .collect(Collectors.toList());
    }

    private void markAsPending(DeliveryOrder deliveryOrder) {
        applyPaymentState(deliveryOrder, false);
    }

    private void markAsSettled(DeliveryOrder deliveryOrder) {
        applyPaymentState(deliveryOrder, true);
    }

    // R8: the only place where sales.paid and deliveryorders.status change, always together. Both entities are
    // managed by the current transaction, so dirty checking persists them on commit
    private void applyPaymentState(DeliveryOrder deliveryOrder, boolean fullyPaid) {
        deliveryOrder.getSale().setPaid(fullyPaid);
        deliveryOrder.setStatus(fullyPaid ? DeliveryStatus.ACTIVO : DeliveryStatus.PENDIENTE);
    }

    private SalePayment buildPayment(Sale sale, Employee employee, PaymentMethod paymentMethod, BigDecimal amount,
                                     String notes, String clientRequestId) {
        return SalePayment.builder()
                .sale(sale)
                .employee(employee)
                .paymentMethod(paymentMethod)
                .amount(amount)
                .datetime(DateTimeUtils.nowUtc())
                .notes(notes)
                .clientRequestId(clientRequestId)
                .build();
    }

    private Customer lockCustomer(Long customerId) {
        return customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + customerId));
    }

    private Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + employeeId));
    }

    private PaymentMethod findPaymentMethodById(Long paymentMethodId) {
        if (paymentMethodId == null) {
            throw new BusinessRuleException("El método de pago es obligatorio cuando el monto es mayor a 0");
        }
        return paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new EntityNotFoundException("Payment method not found with id: " + paymentMethodId));
    }

    private boolean isWalkInCustomer(Customer customer) {
        return Objects.equals(customer.getId(), salesProperties.walkInCustomerId());
    }

    private boolean isAlreadyProcessed(String clientRequestId) {
        return clientRequestId != null && salePaymentRepository.existsByClientRequestId(clientRequestId);
    }

    private static String normalizeRequestId(String clientRequestId) {
        return StringUtils.hasText(clientRequestId) ? clientRequestId.trim() : null;
    }

    private static String notesOrDefault(String notes, String defaultNotes) {
        return StringUtils.hasText(notes) ? notes.trim() : defaultNotes;
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("El monto del abono debe ser mayor a 0");
        }
        return toMoney(amount);
    }

    private static BigDecimal totalBalance(List<PendingNote> notes) {
        return notes.stream()
                .map(PendingNote::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static List<Long> saleIds(List<PendingNote> notes) {
        return notes.stream()
                .map(PendingNote::saleId)
                .collect(Collectors.toList());
    }

    private static BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatMoney(BigDecimal amount) {
        return "$" + toMoney(amount).toPlainString();
    }

    private record PendingNote(DeliveryOrder deliveryOrder, BigDecimal paidAmount) {
        Long saleId() {
            return deliveryOrder.getSale().getId();
        }

        BigDecimal balance() {
            return toMoney(deliveryOrder.getSale().getTotal()).subtract(paidAmount);
        }
    }

    private record NoteApplication(List<AppliedPaymentDto> appliedPayments, BigDecimal appliedTotal,
                                   BigDecimal change) {
    }
}
