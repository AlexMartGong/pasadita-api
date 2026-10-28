package com.pasadita.api.dto.account;

import com.pasadita.api.enums.account.PaymentHistoryAction;
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
public class PaymentHistoryDto {
    private Long historyId;
    private LocalDateTime changedAt;
    private PaymentHistoryAction action;
    private BigDecimal previousAmount;
    private BigDecimal newAmount;
    private Long previousPaymentMethodId;
    private String previousPaymentMethodName;
    private Long newPaymentMethodId;
    private String newPaymentMethodName;
    private String reason;
    private Long employeeId;
    private String employeeName;
    private Long paymentId;
    private Long saleId;
    private Long orderId;
    private Long customerId;
    private String customerName;
}
