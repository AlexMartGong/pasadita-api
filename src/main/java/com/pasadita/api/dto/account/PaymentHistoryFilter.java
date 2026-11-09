package com.pasadita.api.dto.account;

import com.pasadita.api.enums.account.PaymentHistoryAction;

import java.time.LocalDate;

// Optional filters of the global payment history; from/to are Mexico dates, both inclusive
public record PaymentHistoryFilter(LocalDate from, LocalDate to, Long employeeId, Long customerId,
                                   PaymentHistoryAction action) {
}
