package com.pasadita.api.entities;

import com.pasadita.api.utils.DateTimeUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "fiscalId")
@Entity
@Table(name = "customer_fiscal_data", indexes = {
        @Index(name = "idx_rfc", columnList = "rfc")
})
public class CustomerFiscalData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fiscal_id")
    private Long fiscalId;

    @Column(name = "rfc", nullable = false, unique = true, length = 13)
    private String rfc;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(name = "regimen_fiscal", nullable = false, length = 3)
    private String regimenFiscal;

    @Column(name = "codigo_postal_fiscal", nullable = false, length = 5)
    private String codigoPostalFiscal;

    @Column(name = "uso_cfdi", nullable = false, length = 4)
    private String usoCfdi;

    @Column(name = "email_facturacion", nullable = false, length = 100)
    private String emailFacturacion;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = DateTimeUtils.nowUtc();
}
