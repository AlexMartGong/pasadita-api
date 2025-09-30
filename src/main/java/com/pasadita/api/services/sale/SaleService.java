package com.pasadita.api.services.sale;

import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleResponseDto;
import com.pasadita.api.dto.sale.SaleUpdateDto;

import java.util.List;

public interface SaleService {
    List<SaleResponseDto> findAll();

    SaleResponseDto save(SaleCreateDto saleCreateDto);

    SaleResponseDto update(Long id, SaleUpdateDto saleUpdateDto);
}