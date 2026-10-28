package com.pasadita.api.services.account;

import com.pasadita.api.dto.account.PaymentHistoryDto;
import com.pasadita.api.dto.account.PaymentHistoryFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentHistoryService {

    List<PaymentHistoryDto> findByPaymentId(Long paymentId);

    Page<PaymentHistoryDto> search(PaymentHistoryFilter filter, Pageable pageable);
}
