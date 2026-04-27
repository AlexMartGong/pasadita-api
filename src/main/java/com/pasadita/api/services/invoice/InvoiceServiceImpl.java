package com.pasadita.api.services.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasadita.api.dto.invoice.InvoiceCreateDto;
import com.pasadita.api.dto.invoice.InvoiceMapper;
import com.pasadita.api.dto.invoice.InvoiceResponseDto;
import com.pasadita.api.entities.CustomerFiscalData;
import com.pasadita.api.entities.Invoice;
import com.pasadita.api.entities.PaymentMethod;
import com.pasadita.api.entities.Product;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.entities.SaleDetail;
import com.pasadita.api.enums.invoice.InvoiceStatus;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.CustomerFiscalDataRepository;
import com.pasadita.api.repositories.InvoiceRepository;
import com.pasadita.api.repositories.SaleRepository;
import com.pasadita.api.utils.DateTimeUtils;
import io.facturapi.Facturapi;
import io.facturapi.FacturapiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Set<InvoiceStatus> BLOCKING_STATUSES =
            Set.of(InvoiceStatus.PENDIENTE, InvoiceStatus.TIMBRADA);

    private static final Set<InvoiceStatus> FINALIZED_STATUSES =
            Set.of(InvoiceStatus.TIMBRADA, InvoiceStatus.CANCELADA);

    private static final String FACTURAPI_INVOICES_BASE_URL = "https://www.facturapi.io/v2/invoices";
    private static final String FACTURAPI_INVOICE_URL = FACTURAPI_INVOICES_BASE_URL + "/";

    private final InvoiceRepository invoiceRepository;
    private final SaleRepository saleRepository;
    private final CustomerFiscalDataRepository fiscalDataRepository;
    private final InvoiceMapper invoiceMapper;
    private final Facturapi facturapi;
    private final InvoiceErrorPersister invoiceErrorPersister;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String facturapiSecret;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              SaleRepository saleRepository,
                              CustomerFiscalDataRepository fiscalDataRepository,
                              InvoiceMapper invoiceMapper,
                              Facturapi facturapi,
                              InvoiceErrorPersister invoiceErrorPersister,
                              HttpClient httpClient,
                              ObjectMapper objectMapper,
                              @Value("${facturapi.key:}") String facturapiSecret) {
        this.invoiceRepository = invoiceRepository;
        this.saleRepository = saleRepository;
        this.fiscalDataRepository = fiscalDataRepository;
        this.invoiceMapper = invoiceMapper;
        this.facturapi = facturapi;
        this.invoiceErrorPersister = invoiceErrorPersister;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.facturapiSecret = facturapiSecret;
    }

    @Override
    @Transactional
    public Optional<InvoiceResponseDto> createInvoiceRequest(InvoiceCreateDto dto) {
        Sale sale = loadAndValidatePaidSale(dto.getSaleId());
        CustomerFiscalData fiscalData = loadAndValidateFiscalData(dto.getFiscalId());

        if (invoiceRepository.existsBySaleIdAndStatusIn(sale.getId(), BLOCKING_STATUSES)) {
            throw new BusinessRuleException("Sale already has an invoice in progress or stamped");
        }

        Invoice invoice = invoiceMapper.toEntity(dto, sale, fiscalData);
        Invoice saved = invoiceRepository.save(invoice);
        return Optional.of(invoiceMapper.toResponseDto(saved));
    }

    @Override
    @Transactional
    public Optional<InvoiceResponseDto> timbrarInvoice(InvoiceCreateDto dto) {
        Sale sale = saleRepository.findWithDetailsById(dto.getSaleId())
                .orElseThrow(() -> new EntityNotFoundException("Sale not found with id: " + dto.getSaleId()));

        if (Boolean.FALSE.equals(sale.getPaid())) {
            throw new BusinessRuleException("Cannot invoice an unpaid sale");
        }

        CustomerFiscalData fiscalData = loadAndValidateFiscalData(dto.getFiscalId());
        validateSatCatalogData(sale);

        Invoice invoice = findOrCreatePendingInvoice(dto, sale, fiscalData);

        try {
            JsonNode stamped = stampThroughFacturapi(sale, fiscalData);
            applyStampedResult(invoice, stamped);
            Invoice persisted = invoiceRepository.save(invoice);
            return Optional.of(invoiceMapper.toResponseDto(persisted));
        } catch (FacturapiException ex) {
            log.error("Facturapi rejected stamping for sale {} (status={}, code={}, path={}): {}",
                    sale.getId(), ex.getStatusCode(), ex.getErrorCode(), ex.getErrorPath(), ex.getMessage());
            invoiceErrorPersister.markAsError(invoice.getInvoiceId());
            throw new BusinessRuleException("Error al timbrar CFDI: " + ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected failure while stamping CFDI for sale {}", sale.getId(), ex);
            invoiceErrorPersister.markAsError(invoice.getInvoiceId());
            throw new BusinessRuleException("Error inesperado al timbrar CFDI: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceResponseDto> getInvoiceBySaleId(Long saleId) {
        return Optional.of(invoiceRepository.findBySaleId(saleId)
                .map(invoiceMapper::toResponseDto)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found for sale id: " + saleId)));
    }

    private Sale loadAndValidatePaidSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Sale not found with id: " + saleId));
        if (Boolean.FALSE.equals(sale.getPaid())) {
            throw new BusinessRuleException("Cannot invoice an unpaid sale");
        }
        return sale;
    }

    private CustomerFiscalData loadAndValidateFiscalData(Long fiscalId) {
        CustomerFiscalData fiscalData = fiscalDataRepository.findById(fiscalId)
                .orElseThrow(() -> new EntityNotFoundException("Customer fiscal data not found with id: " + fiscalId));
        if (Boolean.FALSE.equals(fiscalData.getActive())) {
            throw new BusinessRuleException("Customer fiscal data is inactive: " + fiscalData.getRfc());
        }
        return fiscalData;
    }

    private void validateSatCatalogData(Sale sale) {
        PaymentMethod paymentMethod = sale.getPaymentMethod();
        if (paymentMethod == null || !StringUtils.hasText(paymentMethod.getClaveFormaPagoSat())) {
            throw new BusinessRuleException("Payment method missing SAT 'forma de pago' code");
        }
        List<SaleDetail> details = sale.getSaleDetails();
        if (details == null || details.isEmpty()) {
            throw new BusinessRuleException("Sale has no items to invoice");
        }
        for (SaleDetail detail : details) {
            Product product = detail.getProduct();
            if (product == null || !StringUtils.hasText(product.getClaveProductoSat())) {
                throw new BusinessRuleException(
                        "Product is missing SAT 'clave de producto/servicio': " +
                                (product != null ? product.getName() : "unknown"));
            }
        }
    }

    private Invoice findOrCreatePendingInvoice(InvoiceCreateDto dto, Sale sale, CustomerFiscalData fiscalData) {
        Optional<Invoice> existing = invoiceRepository.findBySaleId(sale.getId());
        if (existing.isPresent()) {
            Invoice current = existing.get();
            if (FINALIZED_STATUSES.contains(current.getStatus())) {
                throw new BusinessRuleException(
                        "Sale " + sale.getId() + " already has a finalized invoice (status=" + current.getStatus() + ")");
            }
            current.setCustomerFiscalData(fiscalData);
            current.setStatus(InvoiceStatus.PENDIENTE);
            return invoiceRepository.save(current);
        }
        Invoice fresh = invoiceMapper.toEntity(dto, sale, fiscalData);
        return invoiceRepository.save(fresh);
    }

    private JsonNode stampThroughFacturapi(Sale sale, CustomerFiscalData fiscalData) {
        io.facturapi.models.Customer fiscalCustomer = facturapi.customers().create(
                buildCustomerPayload(fiscalData), null);

        List<Map<String, Object>> items = new ArrayList<>(sale.getSaleDetails().size());
        for (SaleDetail detail : sale.getSaleDetails()) {
            io.facturapi.models.Product remoteProduct = facturapi.products().create(
                    buildProductPayload(detail));
            items.add(buildItemPayload(detail, remoteProduct.getId()));
        }

        Map<String, Object> invoicePayload = Map.of(
                "customer", fiscalCustomer.getId(),
                "items", items,
                "payment_form", sale.getPaymentMethod().getClaveFormaPagoSat(),
                "payment_method", "PUE",
                "use", fiscalData.getUsoCfdi(),
                "currency", "MXN"
        );
        return postInvoiceToFacturapi(invoicePayload);
    }

    private JsonNode postInvoiceToFacturapi(Map<String, Object> invoicePayload) {
        try {
            String body = objectMapper.writeValueAsString(invoicePayload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FACTURAPI_INVOICES_BASE_URL))
                    .header("Authorization", "Bearer " + facturapiSecret)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new BusinessRuleException(
                        "Facturapi rechazó factura (HTTP " + status + "): " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new RuntimeException("Falla I/O al timbrar CFDI vía Facturapi", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Timbrado CFDI interrumpido", ex);
        }
    }

    private Map<String, Object> buildCustomerPayload(CustomerFiscalData fiscalData) {
        return Map.of(
                "legal_name", fiscalData.getRazonSocial(),
                "tax_id", fiscalData.getRfc(),
                "tax_system", fiscalData.getRegimenFiscal(),
                "email", fiscalData.getEmailFacturacion(),
                "address", Map.of("zip", fiscalData.getCodigoPostalFiscal())
        );
    }

    private Map<String, Object> buildProductPayload(SaleDetail detail) {
        Product product = detail.getProduct();
        Map<String, Object> tax = Map.of(
                "type", "IVA",
                "rate", BigDecimal.ZERO,
                "factor", "Tasa"
        );
        return Map.of(
                "description", product.getName(),
                "product_key", product.getClaveProductoSat(),
                "price", detail.getUnitPrice(),
                "tax_included", true,
                "unit_key", product.getUnitMeasure() != null ? product.getUnitMeasure().getSatCode() : "H87",
                "sku", String.valueOf(product.getId()),
                "taxes", List.of(tax)
        );
    }

    private Map<String, Object> buildItemPayload(SaleDetail detail, String remoteProductId) {
        return Map.of(
                "quantity", detail.getQuantity(),
                "product", remoteProductId,
                "discount", detail.getDiscount() != null ? detail.getDiscount() : BigDecimal.ZERO
        );
    }

    private void applyStampedResult(Invoice invoice, JsonNode stamped) {
        String remoteId = stamped.get("id").asText();
        String uuid = stamped.get("uuid").asText();
        invoice.setStatus(InvoiceStatus.TIMBRADA);
        invoice.setUuid(uuid);
        invoice.setXmlUrl(FACTURAPI_INVOICE_URL + remoteId + "/xml");
        invoice.setPdfUrl(FACTURAPI_INVOICE_URL + remoteId + "/pdf");
        invoice.setTimbradoAt(DateTimeUtils.nowUtc());
    }

    @Service
    @RequiredArgsConstructor
    static class InvoiceErrorPersister {

        private final InvoiceRepository invoiceRepository;

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void markAsError(Long invoiceId) {
            invoiceRepository.findById(invoiceId).ifPresent(invoice -> {
                invoice.setStatus(InvoiceStatus.ERROR);
                invoiceRepository.save(invoice);
            });
        }
    }
}
