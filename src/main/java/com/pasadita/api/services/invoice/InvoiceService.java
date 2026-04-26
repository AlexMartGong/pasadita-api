package com.pasadita.api.services.invoice;

import com.pasadita.api.dto.invoice.InvoiceCreateDto;
import com.pasadita.api.dto.invoice.InvoiceResponseDto;

import java.util.Optional;

public interface InvoiceService {

    Optional<InvoiceResponseDto> createInvoiceRequest(InvoiceCreateDto dto);

    Optional<InvoiceResponseDto> getInvoiceBySaleId(Long saleId);
}
