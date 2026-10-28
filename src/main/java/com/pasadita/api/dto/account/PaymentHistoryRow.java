package com.pasadita.api.dto.account;

import com.pasadita.api.enums.account.PaymentHistoryAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// JPQL projection of a history row with everything the response needs, loaded in a single query; changedAt is UTC
public record PaymentHistoryRow(Long historyId, LocalDateTime changedAt, PaymentHistoryAction action,
                                BigDecimal previousAmount, BigDecimal newAmount,
                                Long previousPaymentMethodId, String previousPaymentMethodName,
                                Long newPaymentMethodId, String newPaymentMethodName,
                                String reason, Long employeeId, String employeeName,
                                Long paymentId, Long saleId, Long orderId, Long customerId, String customerName) {
}
