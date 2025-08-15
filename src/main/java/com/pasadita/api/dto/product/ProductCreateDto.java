package com.pasadita.api.dto.product;

import com.pasadita.api.enums.product.Category;
import com.pasadita.api.enums.product.UnitMeasure;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateDto {
    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre del producto debe tener entre 2 y 100 caracteres")
    private String name;

    @NotNull(message = "La categoría es obligatoria")
    private Category category;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe de ser mayor que 0")
    @DecimalMax(value = "99999999.99", message = "El precio debe de ser menor que 99999999.99")
    @Digits(integer = 6, fraction = 2, message = "El precio debe tener un máximo de 6 dígitos enteros y 2 decimales")
    private BigDecimal price;

    @NotNull(message = "La unidad de medida es obligatoria")
    private UnitMeasure unitMeasure;

    @NotNull(message = "El estado del producto es obligatorio")
    private boolean active;
}
