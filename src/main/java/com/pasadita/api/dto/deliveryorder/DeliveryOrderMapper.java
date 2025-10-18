package com.pasadita.api.dto.deliveryorder;

import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DeliveryOrderMapper {

    public DeliveryOrder toEntity(DeliveryOrderCreateDto dto, Sale sale, Employee deliveryEmployee) {
        return DeliveryOrder.builder()
                .sale(sale)
                .deliveryEmployee(deliveryEmployee)
                .status(DeliveryStatus.PENDIENTE)
                .requestDate(LocalDateTime.now())
                .deliveryAddress(dto.getDeliveryAddress())
                .contactPhone(dto.getContactPhone())
                .deliveryCost(dto.getDeliveryCost())
                .build();
    }

    public DeliveryOrderResponseDto toResponseDto(DeliveryOrder deliveryOrder) {
        return DeliveryOrderResponseDto.builder()
                .id(deliveryOrder.getId())
                .saleId(deliveryOrder.getSale() != null ? deliveryOrder.getSale().getId() : null)
                .deliveryEmployeeId(deliveryOrder.getDeliveryEmployee() != null ? deliveryOrder.getDeliveryEmployee().getId() : null)
                .deliveryEmployeeName(deliveryOrder.getDeliveryEmployee() != null ? deliveryOrder.getDeliveryEmployee().getFullName() : null)
                .status(deliveryOrder.getStatus())
                .requestDate(deliveryOrder.getRequestDate())
                .deliveryAddress(deliveryOrder.getDeliveryAddress())
                .contactPhone(deliveryOrder.getContactPhone())
                .deliveryCost(deliveryOrder.getDeliveryCost())
                .build();
    }
}
