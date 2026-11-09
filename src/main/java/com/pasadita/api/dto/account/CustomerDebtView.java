package com.pasadita.api.dto.account;

import java.math.BigDecimal;

public interface CustomerDebtView {
    Long getCustomerId();

    String getName();

    String getPhone();

    BigDecimal getCreditLimit();

    BigDecimal getTotalDebt();

    Long getPendingNotes();
}
