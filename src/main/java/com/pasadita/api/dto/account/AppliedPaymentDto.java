package com.pasadita.api.dto.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppliedPaymentDto {
    private Long saleId;
    private BigDecimal amount;
    private BigDecimal remainingBalance;
}
