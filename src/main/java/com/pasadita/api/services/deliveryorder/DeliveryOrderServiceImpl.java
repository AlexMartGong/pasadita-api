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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.utils.DateTimeUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final SaleRepository saleRepository;
    private final EmployeeRepository employeeRepository;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final CustomerRepository customerRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final CustomerAccountService customerAccountService;

    @Override
    @Transactional(readOnly = true)
    public DeliveryOrderSummaryDto findAll() {
        LocalDateTime startOfDayMexico = LocalDate.now(DateTimeUtils.MEXICO_ZONE).atStartOfDay();
        LocalDateTime startOfDay = DateTimeUtils.toUtc(startOfDayMexico);
        LocalDateTime endOfDay = DateTimeUtils.toUtc(startOfDayMexico.plusDays(1));

        List<DeliveryOrder> deliveryOrders = deliveryOrderRepository.findByRequestDateBetween(startOfDay, endOfDay);
        Map<Long, BigDecimal> paidBySaleId = findPaidAmountsBySaleId(deliveryOrders);
        List<DeliveryOrderResponseDto> orderDtos = deliveryOrders.stream()
                .map(order -> deliveryOrderMapper.toResponseDto(order, paidAmountOf(order, paidBySaleId)))
                .collect(Collectors.toList());

        int totalOrders = orderDtos.size();
        BigDecimal totalAmount = orderDtos.stream()
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
    public Optional<DeliveryOrderResponseDto> update(Long id, DeliveryOrderUpdateDto deliveryOrderUpdateDto) {
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found with id: " + id));

        Employee deliveryEmployee = findEmployeeById(deliveryOrderUpdateDto.getDeliveryEmployeeId());

        deliveryOrderMapper.updateEntity(deliveryOrder, deliveryOrderUpdateDto, deliveryEmployee);
        DeliveryOrder updatedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(toResponseDto(updatedDeliveryOrder));
    }

    // READ_COMMITTED + customer lock, like CustomerAccountServiceImpl: the payments check must see a payment that a
    // concurrent collection has just committed for the same note
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<DeliveryOrderResponseDto> changeStatus(Long id, DeliveryOrderChangeStatusDto deliveryOrderChangeStatusDto) {
        // R8: ACTIVO and PENDIENTE are driven only by money (collections and payments)
        if (deliveryOrderChangeStatusDto.getStatus() != DeliveryStatus.CANCELADO) {
            throw new BusinessRuleException("Solo se puede cambiar manualmente un pedido a CANCELADO; ACTIVO y PENDIENTE "
                    + "los cambian el cobro del pedido y los abonos");
        }

        DeliveryOrder deliveryOrder = deliveryOrderRepository.findWithSaleById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery order not found with id: " + id));
        Sale sale = deliveryOrder.getSale();
        customerRepository.findByIdForUpdate(sale.getCustomer().getId());

        // R14: an order whose note has active payments can't be cancelled (cancelled payments don't count)
        if (salePaymentRepository.existsBySaleIdAndStatus(sale.getId(), PaymentStatus.APLICADO)) {
            throw new BusinessRuleException("No se puede cancelar el pedido #" + id + ": su nota tiene abonos registrados");
        }

        sale.setPaid(false);
        saleRepository.save(sale);
        deliveryOrder.setStatus(DeliveryStatus.CANCELADO);
        DeliveryOrder updatedDeliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        return Optional.ofNullable(toResponseDto(updatedDeliveryOrder));
    }

    private DeliveryOrderResponseDto toResponseDto(DeliveryOrder deliveryOrder) {
        Map<Long, BigDecimal> paidBySaleId = findPaidAmountsBySaleId(List.of(deliveryOrder));
        return deliveryOrderMapper.toResponseDto(deliveryOrder, paidAmountOf(deliveryOrder, paidBySaleId));
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
}
