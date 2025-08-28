package com.pasadita.api.dto.customer;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerChangeStatusDto {
    @NotNull(message = "El estado es obligatorio")
    private Boolean active;
}
