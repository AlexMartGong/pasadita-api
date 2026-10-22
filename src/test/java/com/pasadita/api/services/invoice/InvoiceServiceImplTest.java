package com.pasadita.api.services.invoice;

import com.pasadita.api.config.FacturacionProperties;
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
import com.pasadita.api.enums.product.UnitMeasure;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.CustomerFiscalDataRepository;
import com.pasadita.api.repositories.InvoiceRepository;
import com.pasadita.api.repositories.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private CustomerFiscalDataRepository fiscalDataRepository;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private InvoiceServiceImpl.InvoiceErrorPersister invoiceErrorPersister;
    @Mock private HttpClient httpClient;

    private final JsonMapper objectMapper = new JsonMapper();

    private final FacturacionProperties facturacionProperties = new FacturacionProperties(
            new FacturacionProperties.Emisor(
                    "MAMA7908077K3", "ALEJANDRO MARTINEZ MARTINEZ", "626", "46400"),
            new FacturacionProperties.Csd(null, null, null));

    private InvoiceServiceImpl service;

    private Sale sale;
    private CustomerFiscalData fiscalData;
    private Invoice persistedInvoice;
    private InvoiceCreateDto dto;

    @BeforeEach
    void setUp() {
        service = new InvoiceServiceImpl(
                invoiceRepository,
                saleRepository,
                fiscalDataRepository,
                invoiceMapper,
                invoiceErrorPersister,
                httpClient,
                objectMapper,
                facturacionProperties,
                "test-secret"
        );
        Product product = Product.builder()
                .id(10L)
                .name("Plátano")
                .price(new BigDecimal("20.00"))
                .unitMeasure(UnitMeasure.KILOGRAMO)
                .claveProductoSat("50301500")
                .active(true)
                .build();

        SaleDetail detail = SaleDetail.builder()
                .id(100L)
                .product(product)
                .quantity(new BigDecimal("2.000"))
                .unitPrice(new BigDecimal("20.00"))
                .subtotal(new BigDecimal("40.00"))
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("40.00"))
                .build();

        PaymentMethod payment = PaymentMethod.builder()
                .id(1L).name("Efectivo").active(true).claveFormaPagoSat("01").build();

        sale = Sale.builder()
                .id(500L)
                .paymentMethod(payment)
                .subtotal(new BigDecimal("40.00"))
                .total(new BigDecimal("40.00"))
                .amountTendered(new BigDecimal("50.00"))
                .paid(true)
                .saleDetails(List.of(detail))
                .build();
        detail.setSale(sale);

        fiscalData = CustomerFiscalData.builder()
                .fiscalId(7L)
                .rfc("XAXX010101000")
                .razonSocial("PUBLICO EN GENERAL")
                .regimenFiscal("616")
                .codigoPostalFiscal("46400")
                .usoCfdi("S01")
                .emailFacturacion("test@example.com")
                .active(true)
                .build();

        persistedInvoice = Invoice.builder()
                .invoiceId(900L)
                .sale(sale)
                .customerFiscalData(fiscalData)
                .status(InvoiceStatus.PENDIENTE)
                .build();

        dto = new InvoiceCreateDto(sale.getId(), fiscalData.getFiscalId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void timbrarInvoice_happyPath_marksTimbradaAndPersistsFacturapiUrls() throws Exception {
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());
        when(invoiceMapper.toEntity(eq(dto), eq(sale), eq(fiscalData))).thenReturn(persistedInvoice);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenAnswer(inv -> com.pasadita.api.dto.invoice.InvoiceResponseDto.builder()
                        .invoiceId(((Invoice) inv.getArgument(0)).getInvoiceId())
                        .uuid(((Invoice) inv.getArgument(0)).getUuid())
                        .status(((Invoice) inv.getArgument(0)).getStatus().name())
                        .xmlUrl(((Invoice) inv.getArgument(0)).getXmlUrl())
                        .pdfUrl(((Invoice) inv.getArgument(0)).getPdfUrl())
                        .build());

        HttpResponse<String> stampedResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(stampedResponse.statusCode()).thenReturn(200);
        when(stampedResponse.body()).thenReturn(
                "{\"id\":\"inv_abc\",\"uuid\":\"11111111-2222-3333-4444-555555555555\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stampedResponse);

        var response = service.timbrarInvoice(dto);

        assertThat(response).isPresent();
        assertThat(response.get().getStatus()).isEqualTo("TIMBRADA");
        assertThat(response.get().getUuid()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(response.get().getXmlUrl()).isEqualTo("https://www.facturapi.io/v2/invoices/inv_abc/xml");
        assertThat(response.get().getPdfUrl()).isEqualTo("https://www.facturapi.io/v2/invoices/inv_abc/pdf");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = requestCaptor.getValue();
        assertThat(sent.uri().toString()).isEqualTo("https://www.facturapi.io/v2/invoices");
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.headers().firstValue("Authorization")).contains("Bearer test-secret");
        assertThat(sent.headers().firstValue("Content-Type")).contains("application/json");

        // Customer and product travel inline (objects, not catalog ids)
        JsonNode payload = objectMapper.readTree(bodyOf(sent));
        JsonNode customer = payload.get("customer");
        assertThat(customer.isObject()).isTrue();
        assertThat(customer.get("tax_id").asString()).isEqualTo("XAXX010101000");
        assertThat(customer.get("legal_name").asString()).isEqualTo("PUBLICO EN GENERAL");
        assertThat(customer.get("tax_system").asString()).isEqualTo("616");
        assertThat(customer.get("address").get("zip").asString()).isEqualTo("46400");

        JsonNode item = payload.get("items").get(0);
        assertThat(item.get("quantity").decimalValue()).isEqualByComparingTo("2.000");
        JsonNode product = item.get("product");
        assertThat(product.isObject()).isTrue();
        assertThat(product.get("description").asString()).isEqualTo("Plátano");
        assertThat(product.get("product_key").asString()).isEqualTo("50301500");
        assertThat(product.get("price").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(product.get("sku").asString()).isEqualTo("10");

        verify(invoiceErrorPersister, never()).markAsError(any());
        verify(invoiceRepository, times(2)).save(any(Invoice.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timbrarInvoice_manyItems_stampsWithSingleFacturapiRequest() throws Exception {
        // Regression: one remote call per sale line made large sales exceed the client timeout
        int itemCount = 60;
        List<SaleDetail> details = new ArrayList<>(itemCount);
        for (long i = 1; i <= itemCount; i++) {
            Product product = Product.builder()
                    .id(i)
                    .name("Producto " + i)
                    .price(new BigDecimal("15.00"))
                    .unitMeasure(UnitMeasure.KILOGRAMO)
                    .claveProductoSat("50301500")
                    .active(true)
                    .build();
            details.add(SaleDetail.builder()
                    .id(1000L + i)
                    .sale(sale)
                    .product(product)
                    .quantity(new BigDecimal("1.500"))
                    .unitPrice(new BigDecimal("15.00"))
                    .subtotal(new BigDecimal("22.50"))
                    .discount(BigDecimal.ZERO)
                    .total(new BigDecimal("22.50"))
                    .build());
        }
        sale.setSaleDetails(details);
        stubSuccessfulStamping();

        service.timbrarInvoice(dto);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode items = objectMapper.readTree(bodyOf(requestCaptor.getValue())).get("items");
        assertThat(items.size()).isEqualTo(itemCount);
        for (int i = 0; i < itemCount; i++) {
            JsonNode product = items.get(i).get("product");
            assertThat(product.isObject()).isTrue();
            assertThat(product.get("description").asString()).isEqualTo("Producto " + (i + 1));
        }
    }

    @Test
    void timbrarInvoice_resicoPersonaMoral_addsIsrRetentionToProductTaxes() throws Exception {
        // 12-char RFC => persona moral; emisor regimen is 626 (RESICO) => retention applies
        fiscalData.setRfc("ABC101010AB1");
        stubSuccessfulStamping();

        service.timbrarInvoice(dto);

        JsonNode taxes = sentProductTaxes();
        assertThat(taxes.size()).isEqualTo(2);
        JsonNode isr = taxes.get(1);
        assertThat(isr.get("type").asString()).isEqualTo("ISR");
        assertThat(isr.get("rate").decimalValue()).isEqualByComparingTo("0.0125");
        assertThat(isr.get("factor").asString()).isEqualTo("Tasa");
        assertThat(isr.get("withholding").booleanValue()).isTrue();
    }

    @Test
    void timbrarInvoice_personaFisica_doesNotAddIsrRetention() throws Exception {
        // default fiscalData RFC "XAXX010101000" is 13 chars => persona física => no retention
        stubSuccessfulStamping();

        service.timbrarInvoice(dto);

        JsonNode taxes = sentProductTaxes();
        assertThat(taxes.size()).isEqualTo(1);
        assertThat(taxes.get(0).get("type").asString()).isEqualTo("IVA");
    }

    @SuppressWarnings("unchecked")
    private JsonNode sentProductTaxes() throws Exception {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        return objectMapper.readTree(bodyOf(requestCaptor.getValue()))
                .get("items").get(0).get("product").get("taxes");
    }

    // Drains the request's BodyPublisher (the JSON actually sent to Facturapi)
    private static String bodyOf(HttpRequest request) {
        HttpResponse.BodySubscriber<String> subscriber =
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
                subscriber.onNext(List.of(item));
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
        return subscriber.getBody().toCompletableFuture().join();
    }

    private void stubSuccessfulStamping() throws Exception {
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());
        when(invoiceMapper.toEntity(eq(dto), eq(sale), eq(fiscalData))).thenReturn(persistedInvoice);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenReturn(InvoiceResponseDto.builder().status("TIMBRADA").build());

        @SuppressWarnings("unchecked")
        HttpResponse<String> stampedResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(stampedResponse.statusCode()).thenReturn(200);
        when(stampedResponse.body()).thenReturn(
                "{\"id\":\"inv_abc\",\"uuid\":\"11111111-2222-3333-4444-555555555555\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stampedResponse);
    }

    @Test
    void createInvoiceRequest_existingErrorInvoice_reusesSameRow() {
        persistedInvoice.setStatus(InvoiceStatus.ERROR);
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.of(persistedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenAnswer(inv -> InvoiceResponseDto.builder()
                        .invoiceId(((Invoice) inv.getArgument(0)).getInvoiceId())
                        .status(((Invoice) inv.getArgument(0)).getStatus().name())
                        .build());

        var response = service.createInvoiceRequest(dto);

        assertThat(response).isPresent();
        assertThat(response.get().getInvoiceId()).isEqualTo(900L);
        assertThat(response.get().getStatus()).isEqualTo("PENDIENTE");
        assertThat(persistedInvoice.getStatus()).isEqualTo(InvoiceStatus.PENDIENTE);
        assertThat(persistedInvoice.getCustomerFiscalData()).isSameAs(fiscalData);

        verify(invoiceRepository).save(persistedInvoice);
        verify(invoiceMapper, never()).toEntity(any(), any(), any());
    }

    @Test
    void createInvoiceRequest_existingTimbradaInvoice_throwsBusinessRule() {
        persistedInvoice.setStatus(InvoiceStatus.TIMBRADA);
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.of(persistedInvoice));

        assertThatThrownBy(() -> service.createInvoiceRequest(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("finalized");

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void timbrarInvoice_facturapiHttpRejects_preservesRejectionMessage_andMarksError() throws Exception {
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());
        when(invoiceMapper.toEntity(eq(dto), eq(sale), eq(fiscalData))).thenReturn(persistedInvoice);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        HttpResponse<String> badResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(badResponse.statusCode()).thenReturn(400);
        when(badResponse.body()).thenReturn("{\"message\":\"CFDI40147 - uso CFDI inválido\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(badResponse);

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Facturapi rechazó factura")
                .hasMessageContaining("CFDI40147")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("Error inesperado"));

        verify(invoiceErrorPersister).markAsError(persistedInvoice.getInvoiceId());
    }

    @Test
    void timbrarInvoice_unpaidSale_throwsBusinessRule_andSkipsFacturapi() throws Exception {
        sale.setPaid(false);
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unpaid");

        verify(httpClient, never()).send(any(), any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void timbrarInvoice_missingClaveProductoSat_throwsBusinessRule() throws Exception {
        sale.getSaleDetails().getFirst().getProduct().setClaveProductoSat(null);
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("clave de producto");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void timbrarInvoice_missingClaveFormaPago_throwsBusinessRule() throws Exception {
        sale.getPaymentMethod().setClaveFormaPagoSat(null);
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("forma de pago");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void timbrarInvoice_alreadyTimbrada_throwsBusinessRule() throws Exception {
        Invoice already = Invoice.builder()
                .invoiceId(900L)
                .sale(sale)
                .customerFiscalData(fiscalData)
                .status(InvoiceStatus.TIMBRADA)
                .uuid("existing-uuid")
                .build();
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("finalized");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void timbrarInvoice_facturapiTimeout_marksInvoiceAsError() throws Exception {
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());
        when(invoiceMapper.toEntity(eq(dto), eq(sale), eq(fiscalData))).thenReturn(persistedInvoice);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Falla I/O al timbrar CFDI");

        verify(invoiceErrorPersister).markAsError(persistedInvoice.getInvoiceId());
    }

    @Test
    void listInvoices_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Invoice> entityPage = new PageImpl<>(List.of(persistedInvoice), pageable, 1);
        when(invoiceRepository.findAll(pageable)).thenReturn(entityPage);

        InvoiceResponseDto dto = InvoiceResponseDto.builder()
                .invoiceId(persistedInvoice.getInvoiceId())
                .status(InvoiceStatus.PENDIENTE.name())
                .build();
        when(invoiceMapper.toResponseDto(persistedInvoice)).thenReturn(dto);

        Page<InvoiceResponseDto> result = service.listInvoices(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getInvoiceId()).isEqualTo(900L);
        assertThat(result.getContent().getFirst().getStatus()).isEqualTo("PENDIENTE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelInvoice_happyPath_callsFacturapiAndPersistsCancelada() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.TIMBRADA);
        persistedInvoice.setUuid("uuid-123");
        persistedInvoice.setXmlUrl("https://www.facturapi.io/v2/invoices/inv_abc/xml");
        persistedInvoice.setPdfUrl("https://www.facturapi.io/v2/invoices/inv_abc/pdf");
        when(invoiceRepository.findWithDetailsByInvoiceId(900L)).thenReturn(Optional.of(persistedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenAnswer(inv -> InvoiceResponseDto.builder()
                        .invoiceId(((Invoice) inv.getArgument(0)).getInvoiceId())
                        .status(((Invoice) inv.getArgument(0)).getStatus().name())
                        .build());

        HttpResponse<String> okResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(okResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(okResponse);

        InvoiceResponseDto result = service.cancelInvoice(900L, null);

        assertThat(result.getStatus()).isEqualTo("CANCELADA");
        assertThat(persistedInvoice.getStatus()).isEqualTo(InvoiceStatus.CANCELADA);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();
        assertThat(sent.uri().toString()).isEqualTo("https://www.facturapi.io/v2/invoices/inv_abc?motive=02");
        assertThat(sent.method()).isEqualTo("DELETE");
        assertThat(sent.headers().firstValue("Authorization")).contains("Bearer test-secret");
        verify(invoiceRepository).save(persistedInvoice);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelInvoice_explicitMotive_passedAsQueryParam() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.TIMBRADA);
        persistedInvoice.setXmlUrl("https://www.facturapi.io/v2/invoices/inv_xyz/xml");
        when(invoiceRepository.findWithDetailsByInvoiceId(900L)).thenReturn(Optional.of(persistedInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenReturn(InvoiceResponseDto.builder().status("CANCELADA").build());

        HttpResponse<String> okResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(okResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(okResponse);

        service.cancelInvoice(900L, "01");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://www.facturapi.io/v2/invoices/inv_xyz?motive=01");
    }

    @Test
    void cancelInvoice_invoiceNotFound_throwsEntityNotFound() throws Exception {
        when(invoiceRepository.findWithDetailsByInvoiceId(900L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelInvoice(900L, "02"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Invoice not found");

        verify(httpClient, never()).send(any(), any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void cancelInvoice_notTimbrada_throwsBusinessRule() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.PENDIENTE);
        when(invoiceRepository.findWithDetailsByInvoiceId(900L)).thenReturn(Optional.of(persistedInvoice));

        assertThatThrownBy(() -> service.cancelInvoice(900L, "02"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("TIMBRADA");

        verify(httpClient, never()).send(any(), any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendInvoiceEmail_happyPath_postsToFacturapi() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.TIMBRADA);
        persistedInvoice.setXmlUrl("https://www.facturapi.io/v2/invoices/inv_abc/xml");
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.of(persistedInvoice));

        HttpResponse<String> okResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(okResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(okResponse);

        service.sendInvoiceEmail(sale.getId(), "cliente@x.com");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();
        assertThat(sent.uri().toString()).isEqualTo("https://www.facturapi.io/v2/invoices/inv_abc/email");
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.headers().firstValue("Authorization")).contains("Bearer test-secret");
        assertThat(sent.headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    void sendInvoiceEmail_invoiceNotFound_throwsEntityNotFound() throws Exception {
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendInvoiceEmail(sale.getId(), "cliente@x.com"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Invoice not found for sale id");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void sendInvoiceEmail_notTimbrada_throwsBusinessRule() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.PENDIENTE);
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.of(persistedInvoice));

        assertThatThrownBy(() -> service.sendInvoiceEmail(sale.getId(), "cliente@x.com"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("non-stamped");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void sendInvoiceEmail_blankEmail_throwsBusinessRule() throws Exception {
        assertThatThrownBy(() -> service.sendInvoiceEmail(sale.getId(), ""))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Target email");

        verify(invoiceRepository, never()).findBySaleId(any());
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendInvoiceEmail_facturapiRejects_throwsBusinessRule() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.TIMBRADA);
        persistedInvoice.setXmlUrl("https://www.facturapi.io/v2/invoices/inv_abc/xml");
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.of(persistedInvoice));

        HttpResponse<String> badResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(badResponse.statusCode()).thenReturn(400);
        when(badResponse.body()).thenReturn("{\"message\":\"invalid email\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(badResponse);

        assertThatThrownBy(() -> service.sendInvoiceEmail(sale.getId(), "cliente@x.com"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Facturapi rechazó envío");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelInvoice_facturapiRejects_throwsBusinessRule_andLeavesStatusUnchanged() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.TIMBRADA);
        persistedInvoice.setXmlUrl("https://www.facturapi.io/v2/invoices/inv_abc/xml");
        when(invoiceRepository.findWithDetailsByInvoiceId(900L)).thenReturn(Optional.of(persistedInvoice));

        HttpResponse<String> badResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(badResponse.statusCode()).thenReturn(400);
        when(badResponse.body()).thenReturn("{\"message\":\"already cancelled\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(badResponse);

        assertThatThrownBy(() -> service.cancelInvoice(900L, "02"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Facturapi rechazó cancelación");

        assertThat(persistedInvoice.getStatus()).isEqualTo(InvoiceStatus.TIMBRADA);
        verify(invoiceRepository, never()).save(any());
    }

    // Simulates the merge copy returned by save() outside a transaction: same basic columns,
    // but the LAZY sale/customerFiscalData associations are gone (dead proxies in prod).
    private static Invoice detachedCopyOf(Invoice source) {
        return Invoice.builder()
                .invoiceId(source.getInvoiceId())
                .status(source.getStatus())
                .uuid(source.getUuid())
                .xmlUrl(source.getXmlUrl())
                .pdfUrl(source.getPdfUrl())
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void timbrarInvoice_saveReturnsDetachedCopy_mapsWithReattachedAssociations() throws Exception {
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());
        when(invoiceMapper.toEntity(eq(dto), eq(sale), eq(fiscalData))).thenReturn(persistedInvoice);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> detachedCopyOf(inv.getArgument(0)));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenReturn(InvoiceResponseDto.builder().status("TIMBRADA").build());

        HttpResponse<String> stampedResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(stampedResponse.statusCode()).thenReturn(200);
        when(stampedResponse.body()).thenReturn(
                "{\"id\":\"inv_abc\",\"uuid\":\"11111111-2222-3333-4444-555555555555\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stampedResponse);

        var response = service.timbrarInvoice(dto);

        assertThat(response).isPresent();
        ArgumentCaptor<Invoice> mappedCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceMapper).toResponseDto(mappedCaptor.capture());
        assertThat(mappedCaptor.getValue().getSale()).isSameAs(sale);
        assertThat(mappedCaptor.getValue().getCustomerFiscalData()).isSameAs(fiscalData);
    }

    @Test
    @SuppressWarnings("unchecked")
    void timbrarInvoice_persistFailsAfterStamping_doesNotMarkError() throws Exception {
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());
        when(invoiceMapper.toEntity(eq(dto), eq(sale), eq(fiscalData))).thenReturn(persistedInvoice);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new RuntimeException("DB down"));

        HttpResponse<String> stampedResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(stampedResponse.statusCode()).thenReturn(200);
        when(stampedResponse.body()).thenReturn(
                "{\"id\":\"inv_abc\",\"uuid\":\"11111111-2222-3333-4444-555555555555\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stampedResponse);

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(BusinessRuleException.class)
                .hasMessage("DB down");

        verify(invoiceErrorPersister, never()).markAsError(any());
        verify(invoiceMapper, never()).toResponseDto(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelInvoice_saveReturnsDetachedCopy_mapsWithReattachedAssociations() throws Exception {
        persistedInvoice.setStatus(InvoiceStatus.TIMBRADA);
        persistedInvoice.setXmlUrl("https://www.facturapi.io/v2/invoices/inv_abc/xml");
        when(invoiceRepository.findWithDetailsByInvoiceId(900L)).thenReturn(Optional.of(persistedInvoice));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> detachedCopyOf(inv.getArgument(0)));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenReturn(InvoiceResponseDto.builder().status("CANCELADA").build());

        HttpResponse<String> okResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(okResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(okResponse);

        service.cancelInvoice(900L, null);

        ArgumentCaptor<Invoice> mappedCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceMapper).toResponseDto(mappedCaptor.capture());
        assertThat(mappedCaptor.getValue().getSale()).isSameAs(sale);
        assertThat(mappedCaptor.getValue().getCustomerFiscalData()).isSameAs(fiscalData);
    }

    @Test
    void createInvoiceRequest_saveReturnsDetachedCopy_mapsWithReattachedAssociations() {
        persistedInvoice.setStatus(InvoiceStatus.ERROR);
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.of(persistedInvoice));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> detachedCopyOf(inv.getArgument(0)));
        when(invoiceMapper.toResponseDto(any(Invoice.class)))
                .thenReturn(InvoiceResponseDto.builder().status("PENDIENTE").build());

        var response = service.createInvoiceRequest(dto);

        assertThat(response).isPresent();
        ArgumentCaptor<Invoice> mappedCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceMapper).toResponseDto(mappedCaptor.capture());
        assertThat(mappedCaptor.getValue().getSale()).isSameAs(sale);
        assertThat(mappedCaptor.getValue().getCustomerFiscalData()).isSameAs(fiscalData);
    }
}
