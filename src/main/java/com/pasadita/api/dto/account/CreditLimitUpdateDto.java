package com.pasadita.api.dto.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLimitUpdateDto {
    @NotNull(message = "The credit limit is required")
    @DecimalMin(value = "0.00", message = "The credit limit must be non-negative")
    @Digits(integer = 8, fraction = 2, message = "The credit limit must have up to 8 digits and 2 decimal places")
    private BigDecimal creditLimit;
}
