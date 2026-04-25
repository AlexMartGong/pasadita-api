package com.pasadita.api.controllers;

import com.pasadita.api.dto.customer.CustomerTypeCreateDto;
import com.pasadita.api.dto.customer.CustomerTypeResponseDto;
import com.pasadita.api.dto.customer.CustomerTypeUpdateDto;
import com.pasadita.api.services.customer.CustomerTypeService;
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
@RequestMapping("/api/customer-types")
@RequiredArgsConstructor
public class CustomerTypeController {

    private final CustomerTypeService customerTypeService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<CustomerTypeResponseDto>> getAllCustomerTypes() {
        return ResponseEntity.ok(customerTypeService.getAllCustomerTypes());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/save")
    public ResponseEntity<?> saveCustomerType(@Valid @RequestBody CustomerTypeCreateDto customerTypeCreateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(customerTypeService.saveCustomerType(customerTypeCreateDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update")
    public ResponseEntity<?> updateCustomerType(@Valid @RequestBody CustomerTypeUpdateDto customerTypeUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return customerTypeService.updateCustomerType(customerTypeUpdateDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
