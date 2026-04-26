package com.pasadita.api.dto.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponseDto {
    private Long invoiceId;
    private Long saleId;
    private Long fiscalId;
    private String rfc;
    private String razonSocial;
    private String uuid;
    private String status;
    private String xmlUrl;
    private String pdfUrl;
    private LocalDateTime createdAt;
    private LocalDateTime timbradoAt;
}
