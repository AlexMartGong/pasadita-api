package com.pasadita.api.services.product;

import com.pasadita.api.dto.product.ProductChangeStatusDto;
import com.pasadita.api.dto.product.ProductCreateDto;
import com.pasadita.api.dto.product.ProductResponseDto;
import com.pasadita.api.dto.product.ProductUpdateDto;
import com.pasadita.api.dto.product.ProductUpdatePriceDto;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    List<ProductResponseDto> findAll();

    Optional<ProductResponseDto> findById(Long id);

    Optional<ProductResponseDto> save(ProductCreateDto productDto);

    Optional<ProductResponseDto> update(Long id, ProductUpdateDto productUpdateDto);

    Optional<ProductResponseDto> changeStatus(Long id, ProductChangeStatusDto productChangeStatusDto);

    void updatePriceById(Long id, ProductUpdatePriceDto productUpdatePriceDto);


}
