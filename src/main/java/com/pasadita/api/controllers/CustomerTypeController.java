package com.pasadita.api.controllers;

import com.pasadita.api.dto.customer.CustomerTypeCreateDto;
import com.pasadita.api.dto.customer.CustomerTypeResponseDto;
import com.pasadita.api.dto.customer.CustomerTypeUpdateDto;
import com.pasadita.api.services.customer.CustomerTypeService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer-types")
public class CustomerTypeController {

    private final CustomerTypeService customerTypeService;

    public CustomerTypeController(CustomerTypeService customerTypeService) {
        this.customerTypeService = customerTypeService;
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<CustomerTypeResponseDto>> getAllCustomerTypes() {
        List<CustomerTypeResponseDto> customerTypes = customerTypeService.getAllCustomerTypes();
        return ResponseEntity.ok(customerTypes);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/save")
    public ResponseEntity<?> saveCustomerType(@Valid @RequestBody CustomerTypeCreateDto customerTypeCreateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            CustomerTypeResponseDto savedCustomerType = customerTypeService.saveCustomerType(customerTypeCreateDto)
                    .orElseThrow(() -> new RuntimeException("Error al guardar el tipo de cliente"));

            return ResponseEntity.status(HttpStatus.CREATED).body(savedCustomerType);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error al guardar el tipo de cliente: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update")
    public ResponseEntity<?> updateCustomerType(@Valid @RequestBody CustomerTypeUpdateDto customerTypeUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            return customerTypeService.updateCustomerType(customerTypeUpdateDto)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error al actualizar el tipo de cliente: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
