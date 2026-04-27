package com.pasadita.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "facturacion")
public record FacturacionProperties(Emisor emisor, Csd csd) {

    public record Emisor(
            String rfc,
            String razonSocial,
            String regimenFiscal,
            String codigoPostal
    ) {}

    public record Csd(
            String cerPath,
            String keyPath,
            String password
    ) {}
}
