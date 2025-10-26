package com.pasadita.api.dto.deliveryorder;

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
    private LocalDateTime requestDate;
    private String customerName;
    private String deliveryAddress;
    private String contactPhone;
    private boolean paid;
    private BigDecimal total;
}
