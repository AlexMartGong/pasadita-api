package com.pasadita.api.dto.customer;

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
public class CustomerUpdateDto {
    @NotNull(message = "El tipo de cliente es obligatorio")
    private Long customerTypeId;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    private String name;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(min = 10, max = 15, message = "El teléfono debe tener entre 10 y 15 caracteres")
    @Pattern(regexp = "^[0-9]+$", message = "El teléfono solo puede contener números")
    private String phone;

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String address;

    @NotBlank(message = "La ciudad es obligatoria")
    @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres")
    private String city;

    @Size(max = 10, message = "El código postal no puede exceder 10 caracteres")
    private String postalCode;

    @DecimalMin(value = "0.0", inclusive = true, message = "El descuento personalizado debe ser mayor o igual a 0")
    @DecimalMax(value = "100.0", inclusive = true, message = "El descuento personalizado no puede exceder 100%")
    private BigDecimal customDiscount;

    @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
    private String notes;
}
