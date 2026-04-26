package com.pasadita.api.entities;

import com.pasadita.api.enums.invoice.InvoiceStatus;
import com.pasadita.api.utils.DateTimeUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"sale", "customerFiscalData"})
@EqualsAndHashCode(of = "invoiceId")
@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoice_status", columnList = "status"),
        @Index(name = "idx_invoice_uuid", columnList = "uuid")
})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Long invoiceId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", referencedColumnName = "sale_id", nullable = false, unique = true)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_id", referencedColumnName = "fiscal_id", nullable = false)
    private CustomerFiscalData customerFiscalData;

    @Column(name = "uuid", length = 36)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.PENDIENTE;

    @Column(name = "xml_url", columnDefinition = "TEXT")
    private String xmlUrl;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = DateTimeUtils.nowUtc();

    @Column(name = "timbrado_at")
    private LocalDateTime timbradoAt;
}
