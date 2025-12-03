package com.pasadita.api.dto.deliveryorder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderSummaryDto {
    private List<DeliveryOrderResponseDto> orders;
    private int totalOrders;
    private BigDecimal totalAmount;
}