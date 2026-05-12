package com.pasadita.api.controllers;

import com.pasadita.api.dto.fiscal.CustomerFiscalDataCreateDto;
import com.pasadita.api.dto.fiscal.CustomerFiscalDataResponseDto;
import com.pasadita.api.dto.fiscal.CustomerFiscalDataUpdateDto;
import com.pasadita.api.services.fiscal.CustomerFiscalDataService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-fiscal-data")
@RequiredArgsConstructor
public class CustomerFiscalDataController {

    private final CustomerFiscalDataService service;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<List<CustomerFiscalDataResponseDto>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/by-rfc/{rfc}")
    public ResponseEntity<?> getByRfc(@PathVariable String rfc) {
        return ResponseEntity.ok(service.findByRfc(rfc));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PostMapping("/save")
    public ResponseEntity<?> save(@Valid @RequestBody CustomerFiscalDataCreateDto dto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(dto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody CustomerFiscalDataUpdateDto dto,
                                    BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }
        return ResponseEntity.ok(service.update(id, dto));
    }
}
