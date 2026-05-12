package com.pasadita.api.dto.fiscal;

import com.pasadita.api.entities.CustomerFiscalData;
import com.pasadita.api.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

@Component
public class CustomerFiscalDataMapper {

    public CustomerFiscalData toEntity(CustomerFiscalDataCreateDto dto) {
        return CustomerFiscalData.builder()
                .rfc(dto.getRfc())
                .razonSocial(dto.getRazonSocial())
                .regimenFiscal(dto.getRegimenFiscal())
                .codigoPostalFiscal(dto.getCodigoPostalFiscal())
                .usoCfdi(dto.getUsoCfdi())
                .emailFacturacion(dto.getEmailFacturacion())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .active(true)
                .build();
    }

    public void updateEntity(CustomerFiscalData entity, CustomerFiscalDataUpdateDto dto) {
        entity.setRfc(dto.getRfc());
        entity.setRazonSocial(dto.getRazonSocial());
        entity.setRegimenFiscal(dto.getRegimenFiscal());
        entity.setCodigoPostalFiscal(dto.getCodigoPostalFiscal());
        entity.setUsoCfdi(dto.getUsoCfdi());
        entity.setEmailFacturacion(dto.getEmailFacturacion());
        entity.setPhone(dto.getPhone());
        entity.setAddress(dto.getAddress());
        entity.setActive(dto.getActive());
    }

    public CustomerFiscalDataResponseDto toResponseDto(CustomerFiscalData entity) {
        return CustomerFiscalDataResponseDto.builder()
                .fiscalId(entity.getFiscalId())
                .rfc(entity.getRfc())
                .razonSocial(entity.getRazonSocial())
                .regimenFiscal(entity.getRegimenFiscal())
                .codigoPostalFiscal(entity.getCodigoPostalFiscal())
                .usoCfdi(entity.getUsoCfdi())
                .emailFacturacion(entity.getEmailFacturacion())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .active(entity.getActive())
                .createdAt(DateTimeUtils.toMexicoTime(entity.getCreatedAt()))
                .build();
    }
}
