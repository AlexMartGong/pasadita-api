package com.pasadita.api.dto.account;

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
public class CustomerAccountResponseDto {
    private Long customerId;
    private String name;
    private BigDecimal creditLimit;
    private BigDecimal totalDebt;
    private BigDecimal availableCredit;
    private boolean overLimit;
    private List<PendingNoteDto> pendingNotes;
}
