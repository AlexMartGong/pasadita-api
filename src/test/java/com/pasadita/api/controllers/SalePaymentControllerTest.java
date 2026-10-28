package com.pasadita.api.controllers;

import com.pasadita.api.dto.account.PaymentActor;
import com.pasadita.api.dto.account.PaymentCancelDto;
import com.pasadita.api.dto.account.PaymentUpdateDto;
import com.pasadita.api.services.account.CustomerAccountService;
import com.pasadita.api.services.account.PaymentHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SalePaymentControllerTest {

    @Mock private CustomerAccountService customerAccountService;
    @Mock private PaymentHistoryService paymentHistoryService;

    private SalePaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new SalePaymentController(customerAccountService, paymentHistoryService);
    }

    private static Authentication authenticated(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void modificationIsAttributedToTheAuthenticatedCashier() {
        PaymentUpdateDto change = PaymentUpdateDto.builder()
                .amount(new BigDecimal("350")).paymentMethodId(1L).reason("Capturé mal").build();

        controller.modifyPayment(10L, change, new BeanPropertyBindingResult(change, "change"),
                authenticated("cajero1", "ROLE_CAJERO"));

        verify(customerAccountService).modifyPayment(10L, change, new PaymentActor("cajero1", false));
    }

    @Test
    void cancellationCarriesTheAdminFlag() {
        PaymentCancelDto cancellation = PaymentCancelDto.builder().reason("Cobro duplicado").build();

        controller.cancelPayment(10L, cancellation, new BeanPropertyBindingResult(cancellation, "cancellation"),
                authenticated("admin", "ROLE_ADMIN"));

        verify(customerAccountService).cancelPayment(10L, cancellation, new PaymentActor("admin", true));
    }
}
