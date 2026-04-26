package com.pasadita.api.dto.invoice;

import com.pasadita.api.entities.CustomerFiscalData;
import com.pasadita.api.entities.Invoice;
import com.pasadita.api.entities.Sale;
import com.pasadita.api.enums.invoice.InvoiceStatus;
import com.pasadita.api.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

@Component
public class InvoiceMapper {

    public Invoice toEntity(InvoiceCreateDto dto, Sale sale, CustomerFiscalData fiscalData) {
        return Invoice.builder()
                .sale(sale)
                .customerFiscalData(fiscalData)
                .status(InvoiceStatus.PENDIENTE)
                .build();
    }

    public InvoiceResponseDto toResponseDto(Invoice invoice) {
        CustomerFiscalData fiscalData = invoice.getCustomerFiscalData();
        return InvoiceResponseDto.builder()
                .invoiceId(invoice.getInvoiceId())
                .saleId(invoice.getSale() != null ? invoice.getSale().getId() : null)
                .fiscalId(fiscalData != null ? fiscalData.getFiscalId() : null)
                .rfc(fiscalData != null ? fiscalData.getRfc() : null)
                .razonSocial(fiscalData != null ? fiscalData.getRazonSocial() : null)
                .uuid(invoice.getUuid())
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                .xmlUrl(invoice.getXmlUrl())
                .pdfUrl(invoice.getPdfUrl())
                .createdAt(DateTimeUtils.toMexicoTime(invoice.getCreatedAt()))
                .timbradoAt(DateTimeUtils.toMexicoTime(invoice.getTimbradoAt()))
                .build();
    }
}
