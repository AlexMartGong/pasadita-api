package com.pasadita.api.controllers;

import com.pasadita.api.dto.deliveryorder.DeliveryOrderChangeStatusDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderCreateDto;
import com.pasadita.api.dto.deliveryorder.DeliveryOrderResponseDto;
import com.pasadita.api.services.deliveryorder.DeliveryOrderService;
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
@RequestMapping("/api/delivery-orders")
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;

    public DeliveryOrderController(DeliveryOrderService deliveryOrderService) {
        this.deliveryOrderService = deliveryOrderService;
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<DeliveryOrderResponseDto>> getAllDeliveryOrders() {
        List<DeliveryOrderResponseDto> deliveryOrders = deliveryOrderService.findAll();
        return ResponseEntity.ok(deliveryOrders);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/save")
    public ResponseEntity<?> saveDeliveryOrder(@Valid @RequestBody DeliveryOrderCreateDto deliveryOrderCreateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            DeliveryOrderResponseDto responseDto = deliveryOrderService.save(deliveryOrderCreateDto)
                    .orElseThrow(() -> new RuntimeException("No se pudo guardar la orden de entrega"));
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "No se pudo guardar la orden de entrega. Por favor, verifica los datos. " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error saving the delivery order");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PatchMapping("/change-status/{id}")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @Valid @RequestBody DeliveryOrderChangeStatusDto deliveryOrderChangeStatusDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            DeliveryOrderResponseDto responseDto = deliveryOrderService.changeStatus(id, deliveryOrderChangeStatusDto)
                    .orElseThrow(() -> new RuntimeException("No se pudo cambiar el estado de la orden de entrega"));
            return ResponseEntity.ok(responseDto);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "No se pudo cambiar el estado de la orden de entrega. " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error changing the delivery order status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
