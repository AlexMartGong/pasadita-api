package com.pasadita.api.controllers;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderSummaryDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderUpdateDto;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.services.deliveryorder.DeliveryOrderService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery-orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<DeliveryOrderSummaryDto> getAllDeliveryOrders() {
        return ResponseEntity.ok(deliveryOrderService.findAll());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PostMapping("/save")
    public ResponseEntity<DeliveryOrderResponseDto> saveDeliveryOrder(@Valid @RequestBody DeliveryOrderCreateDto deliveryOrderCreateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().build();
        }

        DeliveryOrderResponseDto responseDto = deliveryOrderService.save(deliveryOrderCreateDto)
                .orElseThrow(() -> new EntityNotFoundException("Could not save delivery order"));
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateDeliveryOrder(@PathVariable Long id, @Valid @RequestBody DeliveryOrderUpdateDto deliveryOrderUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(deliveryOrderService.update(id, deliveryOrderUpdateDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @Valid @RequestBody DeliveryOrderChangeStatusDto deliveryOrderChangeStatusDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(deliveryOrderService.changeStatus(id, deliveryOrderChangeStatusDto));
    }
}
