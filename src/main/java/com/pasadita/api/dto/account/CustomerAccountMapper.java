package com.pasadita.api.dto.account;

import com.pasadita.api.entities.Customer;
import com.pasadita.api.entities.DeliveryOrder;
import com.pasadita.api.entities.SalePayment;
import com.pasadita.api.enums.delivery.DeliveryStatus;
import com.pasadita.api.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CustomerAccountMapper {

    public PaymentResultDto toPaymentResultDto(DeliveryStatus orderStatus, BigDecimal saleBalance, BigDecimal customerDebt,
                                               Customer customer, BigDecimal change, List<AppliedPaymentDto> appliedPayments) {
        return PaymentResultDto.builder()
                .orderStatus(orderStatus)
                .saleBalance(saleBalance)
                .customerDebt(customerDebt)
                .creditLimit(customer.getCreditLimit())
                .overLimit(isOverLimit(customerDebt, customer.getCreditLimit()))
                .change(change)
                .appliedPayments(appliedPayments)
                .build();
    }

    public CustomerAccountResponseDto toAccountResponseDto(Customer customer, BigDecimal totalDebt,
                                                           List<PendingNoteDto> pendingNotes) {
        BigDecimal creditLimit = customer.getCreditLimit();
        return CustomerAccountResponseDto.builder()
                .customerId(customer.getId())
                .name(customer.getName())
                .creditLimit(creditLimit)
                .totalDebt(totalDebt)
                .availableCredit(creditLimit.subtract(totalDebt).max(BigDecimal.ZERO))
                .overLimit(isOverLimit(totalDebt, creditLimit))
                .pendingNotes(pendingNotes)
                .build();
    }

    public PendingNoteDto toPendingNoteDto(DeliveryOrder deliveryOrder, BigDecimal paidAmount, BigDecimal balance,
                                           List<PaymentDetailDto> payments) {
        return PendingNoteDto.builder()
                .saleId(deliveryOrder.getSale().getId())
                .orderId(deliveryOrder.getId())
                .datetime(DateTimeUtils.toMexicoTime(deliveryOrder.getSale().getDatetime()))
                .total(deliveryOrder.getSale().getTotal())
                .paidAmount(paidAmount)
                .balance(balance)
                .payments(payments)
                .build();
    }

    public PaymentDetailDto toPaymentDetailDto(SalePayment payment, boolean modified, boolean editable) {
        return PaymentDetailDto.builder()
                .paymentId(payment.getId())
                .datetime(DateTimeUtils.toMexicoTime(payment.getDatetime()))
                .amount(payment.getAmount())
                .paymentMethodId(payment.getPaymentMethod() != null ? payment.getPaymentMethod().getId() : null)
                .paymentMethodName(payment.getPaymentMethod() != null ? payment.getPaymentMethod().getName() : null)
                .employeeId(payment.getEmployee() != null ? payment.getEmployee().getId() : null)
                .employeeName(payment.getEmployee() != null ? payment.getEmployee().getFullName() : null)
                .notes(payment.getNotes())
                .status(payment.getStatus())
                .modified(modified)
                .editable(editable)
                .build();
    }

    public PaymentHistoryDto toPaymentHistoryDto(PaymentHistoryRow row) {
        return PaymentHistoryDto.builder()
                .historyId(row.historyId())
                .changedAt(DateTimeUtils.toMexicoTime(row.changedAt()))
                .action(row.action())
                .previousAmount(row.previousAmount())
                .newAmount(row.newAmount())
                .previousPaymentMethodId(row.previousPaymentMethodId())
                .previousPaymentMethodName(row.previousPaymentMethodName())
                .newPaymentMethodId(row.newPaymentMethodId())
                .newPaymentMethodName(row.newPaymentMethodName())
                .reason(row.reason())
                .employeeId(row.employeeId())
                .employeeName(row.employeeName())
                .paymentId(row.paymentId())
                .saleId(row.saleId())
                .orderId(row.orderId())
                .customerId(row.customerId())
                .customerName(row.customerName())
                .build();
    }

    public CustomerDebtSummaryDto toDebtSummaryDto(CustomerDebtView debtView) {
        return CustomerDebtSummaryDto.builder()
                .customerId(debtView.getCustomerId())
                .name(debtView.getName())
                .phone(debtView.getPhone())
                .creditLimit(debtView.getCreditLimit())
                .totalDebt(debtView.getTotalDebt())
                .pendingNotes(debtView.getPendingNotes() != null ? debtView.getPendingNotes() : 0L)
                .overLimit(isOverLimit(debtView.getTotalDebt(), debtView.getCreditLimit()))
                .build();
    }

    private boolean isOverLimit(BigDecimal debt, BigDecimal creditLimit) {
        return debt.compareTo(creditLimit) > 0;
    }
}
