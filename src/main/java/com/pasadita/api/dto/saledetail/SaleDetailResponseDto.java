package com.pasadita.api.dto.saledetail;

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
public class SaleDetailResponseDto {
    private Long detailId;
    private Long saleId;
    private LocalDateTime saleDate;
    private Long productId;
    private String productName;
    private String productCategory;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal subtotal;
    private BigDecimal total;
}
