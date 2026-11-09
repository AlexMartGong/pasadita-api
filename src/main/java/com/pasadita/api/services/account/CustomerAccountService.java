package com.pasadita.api.services.account;

import com.pasadita.api.dto.account.CustomerAccountResponseDto;
import com.pasadita.api.dto.account.CustomerDebtSummaryDto;
import com.pasadita.api.dto.account.DeliveryCollectionDto;
import com.pasadita.api.dto.account.PaymentActor;
import com.pasadita.api.dto.account.PaymentCancelDto;
import com.pasadita.api.dto.account.PaymentCreateDto;
import com.pasadita.api.dto.account.PaymentDetailDto;
import com.pasadita.api.dto.account.PaymentResultDto;
import com.pasadita.api.dto.account.PaymentUpdateDto;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerAccountService {

    PaymentResultDto registerDeliveryCollection(Long orderId, DeliveryCollectionDto deliveryCollectionDto,
                                                PaymentActor actor);

    PaymentResultDto registerAccountPayment(Long customerId, PaymentCreateDto paymentCreateDto, PaymentActor actor);

    PaymentResultDto registerSalePayment(Long saleId, PaymentCreateDto paymentCreateDto, PaymentActor actor);

    PaymentResultDto modifyPayment(Long paymentId, PaymentUpdateDto paymentUpdateDto, PaymentActor actor);

    PaymentResultDto cancelPayment(Long paymentId, PaymentCancelDto paymentCancelDto, PaymentActor actor);

    List<PaymentDetailDto> findSalePayments(Long saleId, PaymentActor actor);

    CustomerAccountResponseDto getAccount(Long customerId, PaymentActor actor);

    List<CustomerDebtSummaryDto> findCustomersWithDebt();

    CustomerAccountResponseDto updateCreditLimit(Long customerId, BigDecimal creditLimit, PaymentActor actor);

    void assertCreditAllowsNewOrder(Long customerId, boolean requestedByAdmin);
}
