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
    @NotNull(message = "The active status is required")
    private Boolean active;
}
