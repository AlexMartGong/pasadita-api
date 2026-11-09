package com.pasadita.api.services.account;

import com.pasadita.api.dto.account.CustomerAccountMapper;
import com.pasadita.api.dto.account.PaymentHistoryDto;
import com.pasadita.api.dto.account.PaymentHistoryFilter;
import com.pasadita.api.dto.account.PaymentHistoryRow;
import com.pasadita.api.enums.account.PaymentHistoryAction;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.SalePaymentHistoryRepository;
import com.pasadita.api.repositories.SalePaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHistoryServiceImplTest {

    @Mock private SalePaymentHistoryRepository salePaymentHistoryRepository;
    @Mock private SalePaymentRepository salePaymentRepository;

    private PaymentHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentHistoryServiceImpl(salePaymentHistoryRepository, salePaymentRepository,
                new CustomerAccountMapper());
    }

    private static PaymentHistoryRow row(LocalDateTime changedAtUtc) {
        return new PaymentHistoryRow(1L, changedAtUtc, PaymentHistoryAction.MODIFICADO,
                new BigDecimal("400.00"), new BigDecimal("700.00"), 1L, "Efectivo", 4L, "Tarjeta",
                "Capturé mal el monto", 5L, "Cajera", 10L, 50L, 200L, 20L, "Clienta");
    }

    @Test
    void mexicoDatesBecomeAUtcRangeWhoseEndIsTheStartOfTheNextDay() {
        Pageable pageable = PageRequest.of(0, 20);
        when(salePaymentHistoryRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.search(new PaymentHistoryFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27), 5L, 20L,
                PaymentHistoryAction.CANCELADO), pageable);

        // Mexico City is UTC-6
        verify(salePaymentHistoryRepository).search(LocalDateTime.of(2026, 10, 1, 6, 0),
                LocalDateTime.of(2026, 10, 28, 6, 0), 5L, 20L, PaymentHistoryAction.CANCELADO, pageable);
    }

    @Test
    void missingFiltersAreSentAsNull() {
        Pageable pageable = PageRequest.of(0, 20);
        when(salePaymentHistoryRepository.search(null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(row(LocalDateTime.of(2026, 10, 27, 18, 0)))));

        Page<PaymentHistoryDto> page = service.search(new PaymentHistoryFilter(null, null, null, null, null), pageable);

        assertThat(page.getContent()).singleElement().satisfies(history -> {
            assertThat(history.getChangedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 12, 0));
            assertThat(history.getOrderId()).isEqualTo(200L);
            assertThat(history.getNewPaymentMethodName()).isEqualTo("Tarjeta");
        });
    }

    @Test
    void fromAfterToIsRejected() {
        PaymentHistoryFilter filter = new PaymentHistoryFilter(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 27),
                null, null, null);

        assertThatThrownBy(() -> service.search(filter, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(salePaymentHistoryRepository);
    }

    @Test
    void historyOfAnUnknownPaymentIsNotFound() {
        when(salePaymentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByPaymentId(99L)).isInstanceOf(EntityNotFoundException.class);
    }
}
