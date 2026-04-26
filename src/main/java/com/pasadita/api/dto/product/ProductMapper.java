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
                .claveProductoSat(product.getClaveProductoSat())
                .active(product.isActive())
                .build();
    }

    public Product toEntity(ProductCreateDto dto) {
        return Product.builder()
                .name(dto.getName())
                .category(dto.getCategory())
                .price(dto.getPrice())
                .unitMeasure(dto.getUnitMeasure())
                .claveProductoSat(dto.getClaveProductoSat())
                .active(dto.isActive())
                .build();
    }

    public void updateEntityFromDto(Product product, ProductUpdateDto dto) {
        product.setName(dto.getName());
        product.setCategory(dto.getCategory());
        product.setPrice(dto.getPrice());
        product.setUnitMeasure(dto.getUnitMeasure());
        product.setClaveProductoSat(dto.getClaveProductoSat());
        product.setActive(dto.isActive());
    }
}
