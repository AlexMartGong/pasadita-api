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
public class CustomerDebtSummaryDto {
    private Long customerId;
    private String name;
    private String phone;
    private BigDecimal creditLimit;
    private BigDecimal totalDebt;
    private long pendingNotes;
    private boolean overLimit;
}
