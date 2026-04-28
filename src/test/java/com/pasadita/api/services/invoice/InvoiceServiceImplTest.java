package com.pasadita.api.services.invoice;

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
import com.pasadita.api.enums.product.UnitMeasure;
import com.pasadita.api.exceptions.BusinessRuleException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.repositories.CustomerFiscalDataRepository;
import com.pasadita.api.repositories.InvoiceRepository;
import com.pasadita.api.repositories.SaleRepository;
import io.facturapi.Facturapi;
import io.facturapi.FacturapiException;
import io.facturapi.resources.CustomersResource;
import io.facturapi.resources.ProductsResource;
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

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
    @Mock private Facturapi facturapi;
    @Mock private CustomersResource customersResource;
    @Mock private ProductsResource productsResource;
    @Mock private InvoiceServiceImpl.InvoiceErrorPersister invoiceErrorPersister;
    @Mock private HttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                facturapi,
                invoiceErrorPersister,
                httpClient,
                objectMapper,
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

        when(facturapi.customers()).thenReturn(customersResource);
        when(facturapi.products()).thenReturn(productsResource);

        io.facturapi.models.Customer remoteCustomer = new io.facturapi.models.Customer();
        remoteCustomer.setId("cus_1");
        when(customersResource.create(anyMap(), any())).thenReturn(remoteCustomer);

        io.facturapi.models.Product remoteProduct = new io.facturapi.models.Product();
        remoteProduct.setId("prod_1");
        when(productsResource.create(anyMap())).thenReturn(remoteProduct);

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

        verify(invoiceErrorPersister, never()).markAsError(any());
        verify(invoiceRepository, times(2)).save(any(Invoice.class));
    }

    @Test
    void timbrarInvoice_unpaidSale_throwsBusinessRule_andSkipsFacturapi() {
        sale.setPaid(false);
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unpaid");

        verify(facturapi, never()).customers();
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void timbrarInvoice_missingClaveProductoSat_throwsBusinessRule() {
        sale.getSaleDetails().get(0).getProduct().setClaveProductoSat(null);
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("clave de producto");

        verify(facturapi, never()).customers();
    }

    @Test
    void timbrarInvoice_missingClaveFormaPago_throwsBusinessRule() {
        sale.getPaymentMethod().setClaveFormaPagoSat(null);
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("forma de pago");

        verify(facturapi, never()).customers();
    }

    @Test
    void timbrarInvoice_alreadyTimbrada_throwsBusinessRule() {
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

        verify(facturapi, never()).customers();
    }

    @Test
    void timbrarInvoice_facturapiFails_marksInvoiceAsError() throws Exception {
        when(saleRepository.findWithDetailsById(sale.getId())).thenReturn(Optional.of(sale));
        when(fiscalDataRepository.findById(fiscalData.getFiscalId())).thenReturn(Optional.of(fiscalData));
        when(invoiceRepository.findBySaleId(sale.getId())).thenReturn(Optional.empty());
        when(invoiceMapper.toEntity(eq(dto), eq(sale), eq(fiscalData))).thenReturn(persistedInvoice);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        when(facturapi.customers()).thenReturn(customersResource);
        when(customersResource.create(anyMap(), any()))
                .thenThrow(new FacturapiException("RFC inválido", 400, "invalid_tax_id", "tax_id"));

        assertThatThrownBy(() -> service.timbrarInvoice(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Error al timbrar CFDI");

        verify(invoiceErrorPersister).markAsError(persistedInvoice.getInvoiceId());
        verify(httpClient, never()).send(any(), any());
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
        assertThat(result.getContent().get(0).getInvoiceId()).isEqualTo(900L);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("PENDIENTE");
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
}
