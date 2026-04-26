package com.pasadita.api.dto.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFiscalDataResponseDto {
    private Long fiscalId;
    private String rfc;
    private String razonSocial;
    private String regimenFiscal;
    private String codigoPostalFiscal;
    private String usoCfdi;
    private String emailFacturacion;
    private String phone;
    private String address;
    private Boolean active;
    private LocalDateTime createdAt;
}
