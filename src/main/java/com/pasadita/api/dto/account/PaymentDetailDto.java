package com.pasadita.api.dto.account;

import com.pasadita.api.enums.account.PaymentStatus;
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
public class PaymentDetailDto {
    private Long paymentId;
    private LocalDateTime datetime;
    private BigDecimal amount;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long employeeId;
    private String employeeName;
    private String notes;
    private PaymentStatus status;
    // Has a MODIFICADO history row
    private boolean modified;
    // Whether the authenticated user may modify it now (active, own payment of the day or ADMIN, no stamped invoice)
    private boolean editable;
}
