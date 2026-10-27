package com.pasadita.api.dto.account;

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
public class PendingNoteDto {
    private Long saleId;
    private Long orderId;
    private LocalDateTime datetime;
    private BigDecimal total;
    private BigDecimal paidAmount;
    private BigDecimal balance;
    private List<PaymentDetailDto> payments;
}
