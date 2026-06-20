package com.pasadita.api.controllers;

import com.pasadita.api.config.PrinterWebSocketHandler;
import com.pasadita.api.dto.sale.SaleChangeStatusDto;
import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleResponseDto;
import com.pasadita.api.dto.sale.SaleUpdateDto;
import com.pasadita.api.dto.ticket.TicketResponseDto;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.services.sale.SaleService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private static final Logger log = LoggerFactory.getLogger(SaleController.class);

    private final SaleService saleService;
    private final PrinterWebSocketHandler printerWebSocketHandler;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<List<SaleResponseDto>> getAllSales() {
        return ResponseEntity.ok(saleService.findAll());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @PostMapping("/save")
    public ResponseEntity<?> saveSale(@Valid @RequestBody SaleCreateDto saleCreateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        SaleResponseDto responseDto = saleService.save(saleCreateDto)
                .orElseThrow(() -> new EntityNotFoundException("Sale could not be saved"));

        Boolean printTicket = saleCreateDto.getPrintTicket();
        if (printTicket == null || printTicket) {
            sendTicketToPrinterAsync(responseDto.getId(), saleCreateDto.getStationId());
        } else if (saleCreateDto.getPaymentMethodId() != null
                && saleCreateDto.getPaymentMethodId() == 1L) {
            // Venta en efectivo sin ticket: abrir cajón con pulso ligero.
            sendOpenDrawerAsync(saleCreateDto.getStationId());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    private void sendOpenDrawerAsync(String stationId) {
        CompletableFuture.runAsync(() -> {
            try {
                printerWebSocketHandler.sendOpenDrawerCommand(stationId);
            } catch (Exception e) {
                log.error("Error when sending open-drawer command: {}", e.getMessage(), e);
            }
        });
    }

    private void sendTicketToPrinterAsync(Long saleId, String stationId) {
        CompletableFuture.runAsync(() -> {
            try {
                Optional<TicketResponseDto> ticketOpt = saleService.getTicket(saleId);
                if (ticketOpt.isPresent()) {
                    TicketResponseDto ticket = ticketOpt.get();
                    if (stationId != null && !stationId.isBlank()) {
                        printerWebSocketHandler.sendPrintCommand(stationId, ticket);
                    } else {
                        printerWebSocketHandler.sendPrintCommandToAll(ticket);
                    }
                }
            } catch (Exception e) {
                log.error("Error when sending ticket to printer: {}", e.getMessage(), e);
            }
        });
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateSale(@PathVariable Long id, @Valid @RequestBody SaleUpdateDto saleUpdateDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(saleService.update(id, saleUpdateDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/{saleId}/details")
    public ResponseEntity<?> getSaleDetails(@PathVariable Long saleId) {
        Optional<TicketResponseDto> details = saleService.getTicket(saleId);

        if (details.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No details found for the specified sale."));
        }

        return ResponseEntity.ok(details);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @Valid @RequestBody SaleChangeStatusDto changeStatusDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.ok(saleService.changeStatus(id, changeStatusDto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/{saleId}/ticket")
    public ResponseEntity<?> getTicket(@PathVariable Long saleId, @RequestParam(required = false) String stationId) {
        TicketResponseDto ticket = saleService.getTicket(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Sale not found with id: " + saleId));

        sendTicketToPrinterAsync(saleId, stationId);

        return ResponseEntity.ok(ticket);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @PostMapping("/open-drawer")
    public ResponseEntity<?> openDrawer(@RequestParam(required = false) String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "stationId es requerido"));
        }

        sendOpenDrawerAsync(stationId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Comando de apertura enviado a la estación " + stationId
        ));
    }
}
