package com.pasadita.api.dto.account;

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
}
