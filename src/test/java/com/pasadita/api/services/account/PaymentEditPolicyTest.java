package com.pasadita.api.services.account;

import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.SalePayment;
import com.pasadita.api.enums.account.PaymentStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentEditPolicyTest {

    // 12:00 in Mexico City (UTC-6) on 2026-10-27
    private static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 10, 27, 18, 0);

    private final PaymentEditPolicy policy =
            new PaymentEditPolicy(Clock.fixed(NOW_UTC.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    private final Employee owner = Employee.builder().id(5L).build();
    private final Employee otherEmployee = Employee.builder().id(6L).build();

    private SalePayment paymentBy(Employee employee, LocalDateTime datetimeUtc) {
        return SalePayment.builder()
                .id(1L)
                .employee(employee)
                .amount(new BigDecimal("100.00"))
                .datetime(datetimeUtc)
                .build();
    }

    @Test
    void adminCanEditAnyActivePaymentOfAnyDay() {
        SalePayment oldPaymentOfSomeoneElse = paymentBy(otherEmployee, NOW_UTC.minusDays(30));

        assertThat(policy.isEditable(oldPaymentOfSomeoneElse, owner, true, false)).isTrue();
        assertThatCode(() -> policy.assertCanModify(oldPaymentOfSomeoneElse, owner, true)).doesNotThrowAnyException();
    }

    @Test
    void ownerCanEditTheirPaymentOfTheSameMexicoDay() {
        // 06:30 UTC is 00:30 of the same day in Mexico
        SalePayment payment = paymentBy(owner, LocalDateTime.of(2026, 10, 27, 6, 30));

        assertThat(policy.isEditable(payment, owner, false, false)).isTrue();
        assertThatCode(() -> policy.assertCanModify(payment, owner, false)).doesNotThrowAnyException();
    }

    @Test
    void ownerCannotEditTheirPaymentOfAnotherMexicoDay() {
        // 05:30 UTC on the 27th is 23:30 of the 26th in Mexico
        SalePayment payment = paymentBy(owner, LocalDateTime.of(2026, 10, 27, 5, 30));

        assertThat(policy.isEditable(payment, owner, false, false)).isFalse();
        assertThatThrownBy(() -> policy.assertCanModify(payment, owner, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Solo puedes modificar tus abonos del día. Pide al administrador que lo corrija.");
    }

    @Test
    void anotherEmployeeCannotEditThePaymentEvenOnTheSameDay() {
        SalePayment payment = paymentBy(owner, NOW_UTC);

        assertThat(policy.isEditable(payment, otherEmployee, false, false)).isFalse();
        assertThatThrownBy(() -> policy.assertCanModify(payment, otherEmployee, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancelledPaymentsAndStampedNotesAreNeverEditable() {
        SalePayment cancelled = paymentBy(owner, NOW_UTC);
        cancelled.setStatus(PaymentStatus.CANCELADO);
        SalePayment active = paymentBy(owner, NOW_UTC);

        assertThat(policy.isEditable(cancelled, owner, true, false)).isFalse();
        assertThat(policy.isEditable(active, owner, true, true)).isFalse();
    }
}
