package com.pasadita.api.services.invoice;

import com.pasadita.api.dto.invoice.InvoiceCreateDto;
import com.pasadita.api.dto.invoice.InvoiceResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface InvoiceService {

    Optional<InvoiceResponseDto> createInvoiceRequest(InvoiceCreateDto dto);

    Optional<InvoiceResponseDto> timbrarInvoice(InvoiceCreateDto dto);

    Optional<InvoiceResponseDto> getInvoiceBySaleId(Long saleId);

    Page<InvoiceResponseDto> listInvoices(Pageable pageable);

    InvoiceResponseDto cancelInvoice(Long invoiceId, String motive);

    void sendInvoiceEmail(Long saleId, String targetEmail);
}
