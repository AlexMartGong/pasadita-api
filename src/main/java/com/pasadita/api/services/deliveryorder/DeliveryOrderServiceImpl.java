package com.pasadita.api.services.deliveryorder;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderMapper;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderSummaryDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderUpdateDto;
import com.pasadita.api.dto.account.SalePaidAmount;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.enums.account.PaymentStatus;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.repositories.CustomerRepository;
import com.pasadita.api.repositories.DeliveryOrderRepository;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.services.account.CustomerAccountService;
import com.pasadita.api.services.invoice.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.utils.DateTimeUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final SaleRepository saleRepository;
    private final EmployeeRepository employeeRepository;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final CustomerRepository customerRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final CustomerAccountService customerAccountService;
    private final InvoiceService invoiceService;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional(readOnly = true)
    public DeliveryOrderSummaryDto findAll() {
        DayRange today = DayRange.todayInMexico();

        // Today's orders plus every order still PENDIENTE: a balance from an earlier day stays listed until settled
        List<DeliveryOrder> deliveryOrders = deliveryOrderRepository
                .findRequestedBetweenOrWithStatus(today.start(), today.end(), DeliveryStatus.PENDIENTE);
        Map<Long, BigDecimal> paidBySaleId = findPaidAmountsBySaleId(deliveryOrders);
        List<DeliveryOrderResponseDto> orderDtos = deliveryOrders.stream()
                .map(order -> deliveryOrderMapper.toResponseDto(order, paidAmountOf(order, paidBySaleId),
                        today.contains(order.getRequestDate())))
                .collect(Collectors.toList());

        // The summary figures stay the day's: orders from earlier days are listed but never counted
        List<DeliveryOrderResponseDto> todaysOrders = orderDtos.stream()
                .filter(DeliveryOrderResponseDto::isRequestedToday)
                .collect(Collectors.toList());
        int totalOrders = todaysOrders.size();
        BigDecimal totalAmount = todaysOrders.stream()
                .filter(DeliveryOrderResponseDto::isPaid)
                .map(DeliveryOrderResponseDto::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DeliveryOrderSummaryDto.builder()
                .orders(orderDtos)
                .totalOrders(totalOrders)
                .totalAmount(totalAmount)
                .build();
    }

    @Override
    @Transactional
    public Optional<DeliveryOrderResponseDto> save(DeliveryOrderCreateDto deliveryOrderCreateDto, boolean requestedByAdmin) {
        Sale sale = findSaleById(deliveryOrderCreateDto.getSaleId());
        // R1: every delivery order starts ACTIVO on a fully paid sale
        if (!Boolean.TRUE.equals(sale.getPaid())) {
            throw new BusinessRuleException("Solo se puede crear un pedido para una venta pagada (venta #" + sale.getId() + ")");
        }
        customerAccountService.assertCreditAllowsNewOrder(sale.getCustomer().getId(), requestedByAdmin);
        Employee deliveryEmployee = findEmployeeById(deliveryOrderCreateDto.getDeliveryEmployeeId());

        DeliveryOrder deliveryOrder = deliveryOrderMapper.toEntity(deliveryOrderCreateDto, sale, deliveryEmployee);
        DeliveryOrder savedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(toResponseDto(savedDeliveryOrder));
    }

    @Override
    @Transactional
    public Optional<DeliveryOrderResponseDto> update(Long id, DeliveryOrderUpdateDto deliveryOrderUpdateDto) {
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found with id: " + id));

        Employee deliveryEmployee = findEmployeeById(deliveryOrderUpdateDto.getDeliveryEmployeeId());

        deliveryOrderMapper.updateEntity(deliveryOrder, deliveryOrderUpdateDto, deliveryEmployee);
        DeliveryOrder updatedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(toResponseDto(updatedDeliveryOrder));
    }

    // Not transactional: the note's stamped invoice is cancelled at Facturapi between the checks and the write, so no
    // DB connection is held during that call. Strict: if Facturapi fails, the order stays as it was
    @Override
    public Optional<DeliveryOrderResponseDto> changeStatus(Long id, DeliveryOrderChangeStatusDto deliveryOrderChangeStatusDto) {
        // R8: ACTIVO and PENDIENTE are driven only by money (collections and payments)
        if (deliveryOrderChangeStatusDto.getStatus() != DeliveryStatus.CANCELADO) {
            throw new BusinessRuleException("Solo se puede cambiar manualmente un pedido a CANCELADO; ACTIVO y PENDIENTE "
                    + "los cambian el cobro del pedido y los abonos");
        }

        // R14 before Facturapi is touched; it's checked again under the customer lock before writing
        Long saleId = findWithSaleById(id).getSale().getId();
        assertNoActivePayments(id, saleId);
        invoiceService.cancelInvoiceOfCancelledSale(saleId);

        // shortcut: the customer lock can't be held across the Facturapi call, so a collection landing in between
        // makes the write fail after the invoice was cancelled at SAT (and that note can't be invoiced again);
        // revisit if it shows up in practice
        TransactionTemplate readCommitted = new TransactionTemplate(transactionManager);
        readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return readCommitted.execute(status -> cancelOrder(id));
    }

    // READ_COMMITTED + customer lock, like CustomerAccountServiceImpl: the payments check must see a payment that a
    // concurrent collection has just committed for the same note. The DTO is built inside the transaction because
    // the mapper reads the LAZY customer
    private Optional<DeliveryOrderResponseDto> cancelOrder(Long id) {
        DeliveryOrder deliveryOrder = findWithSaleById(id);
        Sale sale = deliveryOrder.getSale();
        customerRepository.findByIdForUpdate(sale.getCustomer().getId());
        assertNoActivePayments(id, sale.getId());

        sale.setPaid(false);
        saleRepository.save(sale);
        deliveryOrder.setStatus(DeliveryStatus.CANCELADO);
        DeliveryOrder updatedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(toResponseDto(updatedDeliveryOrder));
    }

    // R14: an order whose note has active payments can't be cancelled (cancelled payments don't count)
    private void assertNoActivePayments(Long orderId, Long saleId) {
        if (salePaymentRepository.existsBySaleIdAndStatus(saleId, PaymentStatus.APLICADO)) {
            throw new BusinessRuleException("No se puede cancelar el pedido #" + orderId
                    + ": su nota tiene abonos registrados");
        }
    }

    private DeliveryOrder findWithSaleById(Long id) {
        return deliveryOrderRepository.findWithSaleById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found with id: " + id));
    }

    // requestedToday is computed here too: the frontend merges these single-order answers into the delivery list
    private DeliveryOrderResponseDto toResponseDto(DeliveryOrder deliveryOrder) {
        Map<Long, BigDecimal> paidBySaleId = findPaidAmountsBySaleId(List.of(deliveryOrder));
        return deliveryOrderMapper.toResponseDto(deliveryOrder, paidAmountOf(deliveryOrder, paidBySaleId),
                DayRange.todayInMexico().contains(deliveryOrder.getRequestDate()));
    }

    // One aggregate query of active payments for every listed order, whatever its status: the PENDIENTE ones show their
    // remaining balance, and any order with payments (ACTIVO ones settled by payments included) flags hasPayments
    private Map<Long, BigDecimal> findPaidAmountsBySaleId(List<DeliveryOrder> deliveryOrders) {
        List<Long> saleIds = deliveryOrders.stream()
                .filter(order -> order.getSale() != null)
                .map(order -> order.getSale().getId())
                .collect(Collectors.toList());
        if (saleIds.isEmpty()) {
            return Map.of();
        }
        return salePaymentRepository.sumActiveAmountsBySaleIds(saleIds).stream()
                .collect(Collectors.toMap(SalePaidAmount::saleId, SalePaidAmount::paidAmount));
    }

    private BigDecimal paidAmountOf(DeliveryOrder deliveryOrder, Map<Long, BigDecimal> paidBySaleId) {
        return deliveryOrder.getSale() != null
                ? paidBySaleId.getOrDefault(deliveryOrder.getSale().getId(), BigDecimal.ZERO)
                : BigDecimal.ZERO;
    }

    private Sale findSaleById(Long saleId) {
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Sale not found with id: " + saleId));
    }

    private Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + employeeId));
    }

    // Mexico's current day as the UTC range [start, end) that request_date is stored in. The same bounds select the
    // day's orders and flag them, so the list and requestedToday can't disagree
    private record DayRange(LocalDateTime start, LocalDateTime end) {

        static DayRange todayInMexico() {
            LocalDateTime startOfDayMexico = DateTimeUtils.nowMexico().toLocalDate().atStartOfDay();
            return new DayRange(DateTimeUtils.toUtc(startOfDayMexico), DateTimeUtils.toUtc(startOfDayMexico.plusDays(1)));
        }

        boolean contains(LocalDateTime utcDateTime) {
            return utcDateTime != null && !utcDateTime.isBefore(start) && utcDateTime.isBefore(end);
        }
    }
}
