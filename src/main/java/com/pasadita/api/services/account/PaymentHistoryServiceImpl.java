package com.pasadita.api.services.account;

import com.pasadita.api.dto.account.CustomerAccountMapper;
import com.pasadita.api.dto.account.PaymentHistoryDto;
import com.pasadita.api.dto.account.PaymentHistoryFilter;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.SalePaymentHistoryRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentHistoryServiceImpl implements PaymentHistoryService {

    private final SalePaymentHistoryRepository salePaymentHistoryRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final CustomerAccountMapper customerAccountMapper;

    @Override
    public List<PaymentHistoryDto> findByPaymentId(Long paymentId) {
        if (!salePaymentRepository.existsById(paymentId)) {
            throw new EntityNotFoundException("Payment not found with id: " + paymentId);
        }
        return salePaymentHistoryRepository.findRowsByPaymentId(paymentId).stream()
                .map(customerAccountMapper::toPaymentHistoryDto)
                .collect(Collectors.toList());
    }

    // from/to are Mexico dates, both inclusive: [from 00:00, to + 1 day 00:00) in Mexico time, converted to UTC
    @Override
    public Page<PaymentHistoryDto> search(PaymentHistoryFilter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la fecha final");
        }
        LocalDateTime fromUtc = filter.from() != null ? DateTimeUtils.toUtc(filter.from().atStartOfDay()) : null;
        LocalDateTime toUtcExclusive = filter.to() != null
                ? DateTimeUtils.toUtc(filter.to().plusDays(1).atStartOfDay())
                : null;

        return salePaymentHistoryRepository.search(fromUtc, toUtcExclusive, filter.employeeId(), filter.customerId(),
                        filter.action(), pageable)
                .map(customerAccountMapper::toPaymentHistoryDto);
    }
}
