package com.pasadita.api.dto.sale;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleChangeStatusDto {
    @NotNull(message = "The paid status is required")
    private Boolean paid;
}
