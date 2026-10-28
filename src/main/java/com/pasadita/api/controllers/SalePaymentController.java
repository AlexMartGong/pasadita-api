package com.pasadita.api.controllers;

import com.pasadita.api.dto.account.PaymentActor;
import com.pasadita.api.dto.account.PaymentCancelDto;
import com.pasadita.api.dto.account.PaymentHistoryDto;
import com.pasadita.api.dto.account.PaymentHistoryFilter;
import com.pasadita.api.dto.account.PaymentUpdateDto;
import com.pasadita.api.enums.account.PaymentHistoryAction;
import com.pasadita.api.services.account.CustomerAccountService;
import com.pasadita.api.services.account.PaymentHistoryService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sale-payments")
@RequiredArgsConstructor
public class SalePaymentController {

    private final CustomerAccountService customerAccountService;
    private final PaymentHistoryService paymentHistoryService;

    // CAJERO and PEDIDOS can only modify their own payments of the day (checked in the service)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PutMapping("/{paymentId}")
    public ResponseEntity<?> modifyPayment(@PathVariable Long paymentId,
                                           @Valid @RequestBody PaymentUpdateDto paymentUpdateDto,
                                           BindingResult result, Authentication authentication) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(customerAccountService.modifyPayment(paymentId, paymentUpdateDto,
                PaymentActor.from(authentication)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/{paymentId}/cancel")
    public ResponseEntity<?> cancelPayment(@PathVariable Long paymentId,
                                           @Valid @RequestBody PaymentCancelDto paymentCancelDto,
                                           BindingResult result, Authentication authentication) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(customerAccountService.cancelPayment(paymentId, paymentCancelDto,
                PaymentActor.from(authentication)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/{paymentId}/history")
    public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistory(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentHistoryService.findByPaymentId(paymentId));
    }

    // from/to are Mexico dates (yyyy-MM-dd), both inclusive; newest change first
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/history")
    public ResponseEntity<Page<PaymentHistoryDto>> searchHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) PaymentHistoryAction action,
            Pageable pageable) {
        PaymentHistoryFilter filter = new PaymentHistoryFilter(from, to, employeeId, customerId, action);
        return ResponseEntity.ok(paymentHistoryService.search(filter, pageable));
    }
}
