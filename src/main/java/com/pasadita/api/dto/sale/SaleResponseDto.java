package com.pasadita.api.dto.sale;

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
public class SaleResponseDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long customerId;
    private String customerName;
    private Long paymentMethodId;
    private String paymentMethodName;
    private LocalDateTime datetime;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private boolean paid;
    private String notes;
}