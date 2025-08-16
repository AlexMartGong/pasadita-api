package com.pasadita.api.dto.product;

import com.pasadita.api.entities.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public ProductResponseDto toResponseDto(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .price(product.getPrice())
                .unitMeasure(product.getUnitMeasure())
                .active(product.isActive())
                .build();
    }
}
