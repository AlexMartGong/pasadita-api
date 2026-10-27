package com.pasadita.api.dto.account;

import com.pasadita.api.enums.delivery.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultDto {
    // Status and balance of the order/note the operation targeted; null for an account payment
    private DeliveryStatus orderStatus;
    private BigDecimal saleBalance;
    private BigDecimal customerDebt;
    private BigDecimal creditLimit;
    private boolean overLimit;
    private BigDecimal change;
    private List<AppliedPaymentDto> appliedPayments;
}
