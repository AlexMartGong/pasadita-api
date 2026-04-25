package com.pasadita.api.controllers;

import com.pasadita.api.dto.customer.CustomerChangeStatusDto;
import com.pasadita.api.dto.customer.CustomerCreateDto;
import com.pasadita.api.dto.customer.CustomerResponseDto;
import com.pasadita.api.dto.customer.CustomerUpdateDto;
import com.pasadita.api.services.customer.CustomerService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<List<CustomerResponseDto>> getAllCustomers() {
        return ResponseEntity.ok(customerService.findAll());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/save")
    public ResponseEntity<?> saveCustomer(@Valid @RequestBody CustomerCreateDto customerDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.save(customerDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerUpdateDto customerUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(customerService.update(id, customerUpdateDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeCustomerStatus(@PathVariable Long id, @Valid @RequestBody CustomerChangeStatusDto customerChangeStatusDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        customerService.changeStatus(id, customerChangeStatusDto);
        return ResponseEntity.ok(Map.of("message", "The customer status has been changed successfully"));
    }
}
