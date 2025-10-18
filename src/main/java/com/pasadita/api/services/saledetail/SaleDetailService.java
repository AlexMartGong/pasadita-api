package com.pasadita.api.services.saledetail;

import com.pasadita.api.dto.saledetail.SaleDetailCreateDto;
import com.pasadita.api.dto.saledetail.SaleDetailResponseDto;
import com.pasadita.api.dto.saledetail.SaleDetailUpdateDto;

import java.util.List;
import java.util.Optional;

public interface SaleDetailService {

    List<SaleDetailResponseDto> findAll();

    Optional<SaleDetailResponseDto> findById(Long id);

    List<SaleDetailResponseDto> findBySaleId(Long saleId);

    List<SaleDetailResponseDto> findByProductId(Long productId);

    Optional<SaleDetailResponseDto> save(SaleDetailCreateDto saleDetailDto);

    Optional<SaleDetailResponseDto> update(Long id, SaleDetailUpdateDto saleDetailUpdateDto);

    void deleteById(Long id);
}
