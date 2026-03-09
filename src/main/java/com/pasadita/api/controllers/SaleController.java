package com.pasadita.api.controllers;

import com.pasadita.api.config.PrinterWebSocketHandler;
import com.pasadita.api.dto.sale.SaleChangeStatusDto;
import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleResponseDto;
import com.pasadita.api.dto.sale.SaleUpdateDto;
import com.pasadita.api.dto.ticket.TicketResponseDto;
import com.pasadita.api.services.sale.SaleService;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;
    private final PrinterWebSocketHandler printerWebSocketHandler;

    public SaleController(SaleService saleService, PrinterWebSocketHandler printerWebSocketHandler) {
        this.saleService = saleService;
        this.printerWebSocketHandler = printerWebSocketHandler;
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<List<SaleResponseDto>> getAllSales() {
        List<SaleResponseDto> sales = saleService.findAll();
        return ResponseEntity.ok(sales);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PostMapping("/save")
    public ResponseEntity<?> saveSale(@Valid @RequestBody SaleCreateDto saleCreateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            SaleResponseDto responseDto = saleService.save(saleCreateDto)
                    .orElseThrow(() -> new RuntimeException("Error saving the sale"));

            sendTicketToPrinterAsync(responseDto.getId(), saleCreateDto.getStationId());

            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (RuntimeException e) {

            Map<String, String> error = new HashMap<>();
            error.put("error", "Could not save the sale" + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {

            Map<String, String> error = new HashMap<>();
            error.put("error", "Error saving the sale");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    private void sendTicketToPrinterAsync(Long saleId, String stationId) {
        CompletableFuture.runAsync(() -> {
            try {
                Optional<TicketResponseDto> ticketOpt = saleService.getTicket(saleId);
                if (ticketOpt.isPresent()) {
                    TicketResponseDto ticket = ticketOpt.get();
                    System.out.println("Ticket: " + ticket);
                    if (stationId != null && !stationId.isBlank()) {
                        printerWebSocketHandler.sendPrintCommand(stationId, ticket);
                    } else {
                        printerWebSocketHandler.sendPrintCommandToAll(ticket);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error when sending ticket to printer: " + e.getMessage());
            }
        });
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateSale(@PathVariable Long id, @Valid @RequestBody SaleUpdateDto saleUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            Optional<SaleResponseDto> updatedSale = saleService.update(id, saleUpdateDto);
            return ResponseEntity.ok(updatedSale);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error updating the sale");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/{saleId}/details")
    public ResponseEntity<?> getSaleDetails(@PathVariable Long saleId) {
        try {
            Optional<TicketResponseDto> details = saleService.getTicket(saleId);

            if (details.isEmpty()) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "No details found for the specified sale.");
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.ok(details);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error retrieving sale details: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @Valid @RequestBody SaleChangeStatusDto changeStatusDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        try {
            Optional<SaleResponseDto> updatedSale = saleService.changeStatus(id, changeStatusDto);
            return ResponseEntity.ok(updatedSale);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error changing sale status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/{saleId}/ticket")
    public ResponseEntity<?> getTicket(@PathVariable Long saleId, @RequestParam(required = false) String stationId) {
        try {
            Optional<TicketResponseDto> ticket = saleService.getTicket(saleId);

            if (ticket.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Sale not found with id: " + saleId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            sendTicketToPrinterAsync(saleId, stationId);

            return ResponseEntity.ok(ticket.get());
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error retrieving ticket: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
