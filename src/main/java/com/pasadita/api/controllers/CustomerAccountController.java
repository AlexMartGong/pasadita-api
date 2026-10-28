package com.pasadita.api.controllers;

import com.pasadita.api.dto.account.CreditLimitUpdateDto;
import com.pasadita.api.dto.account.CustomerAccountResponseDto;
import com.pasadita.api.dto.account.CustomerDebtSummaryDto;
import com.pasadita.api.dto.account.DeliveryCollectionDto;
import com.pasadita.api.dto.account.PaymentActor;
import com.pasadita.api.dto.account.PaymentCreateDto;
import com.pasadita.api.dto.account.PaymentDetailDto;
import com.pasadita.api.services.account.CustomerAccountService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CustomerAccountController {

    private final CustomerAccountService customerAccountService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PostMapping("/delivery-orders/{orderId}/collection")
    public ResponseEntity<?> registerDeliveryCollection(@PathVariable Long orderId,
                                                        @Valid @RequestBody DeliveryCollectionDto deliveryCollectionDto,
                                                        BindingResult result, Authentication authentication) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(customerAccountService.registerDeliveryCollection(orderId, deliveryCollectionDto,
                PaymentActor.from(authentication)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PostMapping("/customers/{customerId}/payments")
    public ResponseEntity<?> registerAccountPayment(@PathVariable Long customerId,
                                                    @Valid @RequestBody PaymentCreateDto paymentCreateDto,
                                                    BindingResult result, Authentication authentication) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(customerAccountService.registerAccountPayment(customerId, paymentCreateDto,
                PaymentActor.from(authentication)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/sales/{saleId}/payments")
    public ResponseEntity<?> registerSalePayment(@PathVariable Long saleId,
                                                 @Valid @RequestBody PaymentCreateDto paymentCreateDto,
                                                 BindingResult result, Authentication authentication) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(customerAccountService.registerSalePayment(saleId, paymentCreateDto,
                PaymentActor.from(authentication)));
    }

    // Active and cancelled payments of the note, each with editable computed for the authenticated user
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/sales/{saleId}/payments")
    public ResponseEntity<List<PaymentDetailDto>> getSalePayments(@PathVariable Long saleId,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(customerAccountService.findSalePayments(saleId, PaymentActor.from(authentication)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/customers/{customerId}/account")
    public ResponseEntity<CustomerAccountResponseDto> getAccount(@PathVariable Long customerId,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(customerAccountService.getAccount(customerId, PaymentActor.from(authentication)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/customers/debts")
    public ResponseEntity<List<CustomerDebtSummaryDto>> getCustomersWithDebt() {
        return ResponseEntity.ok(customerAccountService.findCustomersWithDebt());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PatchMapping("/customers/{customerId}/credit-limit")
    public ResponseEntity<?> updateCreditLimit(@PathVariable Long customerId,
                                               @Valid @RequestBody CreditLimitUpdateDto creditLimitUpdateDto,
                                               BindingResult result, Authentication authentication) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(customerAccountService.updateCreditLimit(customerId,
                creditLimitUpdateDto.getCreditLimit(), PaymentActor.from(authentication)));
    }
}
