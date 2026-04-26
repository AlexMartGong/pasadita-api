package com.pasadita.api.dto.invoice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCreateDto {

    @NotNull(message = "The sale ID is required")
    @Positive(message = "The sale ID must be a positive number")
    private Long saleId;

    @NotNull(message = "The fiscal ID is required")
    @Positive(message = "The fiscal ID must be a positive number")
    private Long fiscalId;
}
