package com.pasadita.api.dto.product;

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
public class ProductUpdatePriceDto {
    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe de ser mayor que 0")
    @DecimalMax(value = "99999999.99", message = "El precio debe de ser menor que 99999999.99")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener un máximo de 8 dígitos enteros y 2 decimales")
    private BigDecimal price;
}
