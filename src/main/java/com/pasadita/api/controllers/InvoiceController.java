package com.pasadita.api.controllers;

import com.pasadita.api.dto.invoice.InvoiceCreateDto;
import com.pasadita.api.services.invoice.InvoiceService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @PostMapping
    public ResponseEntity<?> createInvoiceRequest(@Valid @RequestBody InvoiceCreateDto dto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.createInvoiceRequest(dto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @GetMapping("/sale/{saleId}")
    public ResponseEntity<?> getInvoiceBySaleId(@PathVariable Long saleId) {
        return ResponseEntity.ok(invoiceService.getInvoiceBySaleId(saleId));
    }
}
