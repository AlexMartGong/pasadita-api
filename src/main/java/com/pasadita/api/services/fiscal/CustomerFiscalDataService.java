package com.pasadita.api.services.fiscal;

import com.pasadita.api.dto.fiscal.CustomerFiscalDataCreateDto;
import com.pasadita.api.dto.fiscal.CustomerFiscalDataResponseDto;
import com.pasadita.api.dto.fiscal.CustomerFiscalDataUpdateDto;

import java.util.List;
import java.util.Optional;

public interface CustomerFiscalDataService {

    List<CustomerFiscalDataResponseDto> findAll();

    Optional<CustomerFiscalDataResponseDto> findById(Long id);

    Optional<CustomerFiscalDataResponseDto> findByRfc(String rfc);

    Optional<CustomerFiscalDataResponseDto> save(CustomerFiscalDataCreateDto dto);

    Optional<CustomerFiscalDataResponseDto> update(Long id, CustomerFiscalDataUpdateDto dto);
}
