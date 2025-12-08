package com.pasadita.api.dto.sale;

import com.pasadita.api.dto.saledetail.SaleDetailResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponseDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeePhone;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private Long paymentMethodId;
    private String paymentMethodName;
    private LocalDateTime datetime;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private boolean paid;
    private String notes;
    private List<SaleDetailResponseDto> saleDetails;
    private Long deliveryOrderId;
    private String deliveryAddress;
}