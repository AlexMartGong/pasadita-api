package com.pasadita.api.services.sale;

import com.pasadita.api.dto.sale.SaleCreateDto;
import com.pasadita.api.dto.sale.SaleResponseDto;
import com.pasadita.api.dto.sale.SaleUpdateDto;
import com.pasadita.api.dto.saledetail.SaleDetailResponseDto;

import java.util.List;
import java.util.Optional;

public interface SaleService {
    List<SaleResponseDto> findAll();

    Optional<SaleResponseDto> save(SaleCreateDto saleCreateDto);

    Optional<SaleResponseDto> update(Long id, SaleUpdateDto saleUpdateDto);

    List<SaleDetailResponseDto> getSaleDetails(Long saleId);
}