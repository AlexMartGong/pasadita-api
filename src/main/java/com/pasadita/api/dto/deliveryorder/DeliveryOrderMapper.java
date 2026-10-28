package com.pasadita.api.dto.deliveryorder;

import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


@Component
public class DeliveryOrderMapper {

    public DeliveryOrder toEntity(DeliveryOrderCreateDto dto, Sale sale, Employee deliveryEmployee) {
        return DeliveryOrder.builder()
                .sale(sale)
                .deliveryEmployee(deliveryEmployee)
                .status(DeliveryStatus.ACTIVO)
                .requestDate(DateTimeUtils.nowUtc())
                .deliveryAddress(dto.getDeliveryAddress())
                .contactPhone(dto.getContactPhone())
                .deliveryCost(dto.getDeliveryCost())
                .build();
    }

    public DeliveryOrder toEntity(DeliveryOrderEmbeddedDto dto, Sale sale, Employee deliveryEmployee) {
        return DeliveryOrder.builder()
                .sale(sale)
                .deliveryEmployee(deliveryEmployee)
                .status(DeliveryStatus.ACTIVO)
                .requestDate(DateTimeUtils.nowUtc())
                .deliveryAddress(dto.getDeliveryAddress())
                .contactPhone(dto.getContactPhone())
                .deliveryCost(dto.getDeliveryCost())
                .build();
    }

    public void updateEntity(DeliveryOrder deliveryOrder, DeliveryOrderUpdateDto dto, Employee deliveryEmployee) {
        deliveryOrder.setDeliveryEmployee(deliveryEmployee);
        deliveryOrder.setDeliveryAddress(dto.getDeliveryAddress());
        deliveryOrder.setContactPhone(dto.getContactPhone());
        deliveryOrder.setDeliveryCost(dto.getDeliveryCost());
    }

    // paidAmount = active payments (abonos) on the order's note; only a PENDIENTE order carries a balance. Active
    // payments are never below 0.01, so a positive sum means the note has some
    public DeliveryOrderResponseDto toResponseDto(DeliveryOrder deliveryOrder, BigDecimal paidAmount) {
        BigDecimal total = deliveryOrder.getSale() != null ? deliveryOrder.getSale().getTotal() : null;
        BigDecimal balance = deliveryOrder.getStatus() == DeliveryStatus.PENDIENTE && total != null
                ? total.subtract(paidAmount)
                : BigDecimal.ZERO;
        return DeliveryOrderResponseDto.builder()
                .id(deliveryOrder.getId())
                .saleId(deliveryOrder.getSale() != null ? deliveryOrder.getSale().getId() : null)
                .customerName(deliveryOrder.getSale() != null ? deliveryOrder.getSale().getCustomer().getName() : null)
                .requestDate(DateTimeUtils.toMexicoTime(deliveryOrder.getRequestDate()))
                .deliveryAddress(deliveryOrder.getDeliveryAddress())
                .contactPhone(deliveryOrder.getContactPhone())
                .paid(deliveryOrder.getSale() != null ? deliveryOrder.getSale().getPaid() : false)
                .total(total)
                .balance(balance)
                .hasPayments(paidAmount.signum() > 0)
                .status(deliveryOrder.getStatus())
                .build();
    }
}
