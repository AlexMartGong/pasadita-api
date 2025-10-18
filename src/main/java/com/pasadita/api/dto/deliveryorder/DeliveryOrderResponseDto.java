package com.pasadita.api.dto.deliveryorder;

import com.pasadita.api.enums.delivery.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderResponseDto {
    private Long id;
    private Long saleId;
    private Long deliveryEmployeeId;
    private String deliveryEmployeeName;
    private DeliveryStatus status;
    private LocalDateTime requestDate;
    private String deliveryAddress;
    private String contactPhone;
    private BigDecimal deliveryCost;
}
