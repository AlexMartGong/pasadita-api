package com.pasadita.api.services.account;

import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.SalePayment;
import com.pasadita.api.enums.account.PaymentStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Who may modify a payment (M6): an ADMIN any payment, any day; CAJERO and PEDIDOS only the payments they recorded
 * themselves on the same Mexico day. {@link #isEditable} adds the status rule (M3), so the frontend gets one flag
 * instead of repeating them. A stamped invoice on the note doesn't freeze its payments.
 */
@Component
@RequiredArgsConstructor
public class PaymentEditPolicy {

    private final Clock clock;

    public boolean isEditable(SalePayment payment, Employee actorEmployee, boolean admin) {
        return payment.getStatus() == PaymentStatus.APLICADO
                && isOwnerSameDayOrAdmin(payment, actorEmployee, admin);
    }

    public void assertCanModify(SalePayment payment, Employee actorEmployee, boolean admin) {
        if (!isOwnerSameDayOrAdmin(payment, actorEmployee, admin)) {
            throw new BusinessRuleException("Solo puedes modificar tus abonos del día. Pide al administrador que lo corrija.");
        }
    }

    private boolean isOwnerSameDayOrAdmin(SalePayment payment, Employee actorEmployee, boolean admin) {
        if (admin) {
            return true;
        }
        boolean recordedByActor = payment.getEmployee() != null
                && Objects.equals(payment.getEmployee().getId(), actorEmployee.getId());
        LocalDate paymentDay = DateTimeUtils.toMexicoTime(payment.getDatetime()).toLocalDate();
        return recordedByActor && paymentDay.equals(LocalDate.now(clock.withZone(DateTimeUtils.MEXICO_ZONE)));
    }
}
