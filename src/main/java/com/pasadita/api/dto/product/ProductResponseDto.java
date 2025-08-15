package com.pasadita.api.dto.product;

import com.pasadita.api.enums.product.Category;
import com.pasadita.api.enums.product.UnitMeasure;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDto {
    private Long id;
    private String name;
    private Category category;
    private BigDecimal price;
    private UnitMeasure unitMeasure;
    private boolean active;
}
