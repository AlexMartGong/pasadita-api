package com.pasadita.api.controllers;

import com.pasadita.api.dto.invoice.InvoiceCreateDto;
import com.pasadita.api.dto.invoice.InvoiceResponseDto;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.services.invoice.InvoiceService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private static final String STAMPED_STATUS = "TIMBRADA";

    private final InvoiceService invoiceService;
    private final HttpClient httpClient;
    private final String facturapiSecret;

    public InvoiceController(InvoiceService invoiceService,
                             HttpClient httpClient,
                             @Value("${facturapi.key:}") String facturapiSecret) {
        this.invoiceService = invoiceService;
        this.httpClient = httpClient;
        this.facturapiSecret = facturapiSecret;
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @PostMapping
    public ResponseEntity<?> createInvoiceRequest(@Valid @RequestBody InvoiceCreateDto dto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.createInvoiceRequest(dto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @GetMapping
    public ResponseEntity<Page<InvoiceResponseDto>> listInvoices(Pageable pageable) {
        return ResponseEntity.ok(invoiceService.listInvoices(pageable));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @DeleteMapping("/{invoiceId}")
    public ResponseEntity<InvoiceResponseDto> cancelInvoice(
            @PathVariable Long invoiceId,
            @RequestParam(defaultValue = "02") String motive) {
        return ResponseEntity.ok(invoiceService.cancelInvoice(invoiceId, motive));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @PostMapping("/timbrar")
    public ResponseEntity<?> timbrarInvoice(@Valid @RequestBody InvoiceCreateDto dto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }
        return ResponseEntity.ok(invoiceService.timbrarInvoice(dto));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @GetMapping("/sale/{saleId}")
    public ResponseEntity<?> getInvoiceBySaleId(@PathVariable Long saleId) {
        return ResponseEntity.ok(invoiceService.getInvoiceBySaleId(saleId));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @GetMapping("/sale/{saleId}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long saleId) {
        InvoiceResponseDto invoice = requireStampedInvoice(saleId);
        return downloadFromFacturapi(
                invoice.getPdfUrl(),
                MediaType.APPLICATION_PDF,
                "factura_venta_" + saleId + ".pdf");
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO')")
    @GetMapping("/sale/{saleId}/xml")
    public ResponseEntity<byte[]> downloadInvoiceXml(@PathVariable Long saleId) {
        InvoiceResponseDto invoice = requireStampedInvoice(saleId);
        return downloadFromFacturapi(
                invoice.getXmlUrl(),
                MediaType.APPLICATION_XML,
                "factura_venta_" + saleId + ".xml");
    }

    private InvoiceResponseDto requireStampedInvoice(Long saleId) {
        InvoiceResponseDto invoice = invoiceService.getInvoiceBySaleId(saleId)
                .orElseThrow(() -> new BusinessRuleException("Invoice not found for sale " + saleId));
        if (!STAMPED_STATUS.equals(invoice.getStatus())) {
            throw new BusinessRuleException(
                    "Invoice for sale " + saleId + " is not stamped (status=" + invoice.getStatus() + ")");
        }
        return invoice;
    }

    private ResponseEntity<byte[]> downloadFromFacturapi(String url, MediaType contentType, String filename) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + facturapiSecret)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException(
                        "Facturapi rechazó descarga (HTTP " + status + ") para URL " + url);
            }
            byte[] body = response.body();
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(body.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(body);
        } catch (IOException ex) {
            throw new RuntimeException("Falla I/O al descargar documento desde Facturapi", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Descarga de documento interrumpida", ex);
        }
    }
}
