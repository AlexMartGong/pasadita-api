package com.pasadita.api.controllers;

import com.pasadita.api.dto.customer.CustomerChangeStatusDto;
import com.pasadita.api.dto.customer.CustomerCreateDto;
import com.pasadita.api.dto.customer.CustomerResponseDto;
import com.pasadita.api.dto.customer.CustomerUpdateDto;
import com.pasadita.api.services.customer.CustomerService;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<List<CustomerResponseDto>> getAllCustomers() {
        List<CustomerResponseDto> customers = customerService.findAll();
        return ResponseEntity.ok(customers);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/save")
    public ResponseEntity<?> saveCustomer(@Valid @RequestBody CustomerCreateDto customerDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            CustomerResponseDto savedCustomer = customerService.save(customerDto)
                    .orElseThrow(() -> new RuntimeException("Error saving the customer"));

            return ResponseEntity.status(HttpStatus.CREATED).body(savedCustomer);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error saving the customer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerUpdateDto customerUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            CustomerResponseDto updatedCustomer = customerService.update(id, customerUpdateDto)
                    .orElseThrow(() -> new RuntimeException("Error updating the customer"));

            return ResponseEntity.ok(updatedCustomer);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error updating the customer: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeCustomerStatus(@PathVariable Long id, @Valid @RequestBody CustomerChangeStatusDto customerChangeStatusDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            customerService.changeStatus(id, customerChangeStatusDto)
                    .orElseThrow(() -> new RuntimeException("Error changing the customer status"));

            Map<String, String> response = new HashMap<>();
            response.put("message", "The customer status has been changed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error changing the customer status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
