package com.pasadita.api.dto.account;

import java.math.BigDecimal;

public record SalePaidAmount(Long saleId, BigDecimal paidAmount) {
}
