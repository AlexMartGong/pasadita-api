package com.pasadita.api.services.account;

import com.pasadita.api.dto.account.CustomerAccountResponseDto;
import com.pasadita.api.dto.account.CustomerDebtSummaryDto;
import com.pasadita.api.dto.account.DeliveryCollectionDto;
import com.pasadita.api.dto.account.PaymentCreateDto;
import com.pasadita.api.dto.account.PaymentResultDto;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerAccountService {

    PaymentResultDto registerDeliveryCollection(Long orderId, DeliveryCollectionDto deliveryCollectionDto);

    PaymentResultDto registerAccountPayment(Long customerId, PaymentCreateDto paymentCreateDto);

    PaymentResultDto registerSalePayment(Long saleId, PaymentCreateDto paymentCreateDto);

    CustomerAccountResponseDto getAccount(Long customerId);

    List<CustomerDebtSummaryDto> findCustomersWithDebt();

    CustomerAccountResponseDto updateCreditLimit(Long customerId, BigDecimal creditLimit);

    void assertCreditAllowsNewOrder(Long customerId, boolean requestedByAdmin);
}
