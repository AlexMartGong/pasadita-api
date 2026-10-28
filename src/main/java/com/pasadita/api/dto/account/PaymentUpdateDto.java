package com.pasadita.api.dto.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentUpdateDto {
    @NotNull(message = "The amount is required")
    @DecimalMin(value = "0.01", message = "The amount must be at least 0.01")
    @Digits(integer = 10, fraction = 2, message = "The amount must have up to 10 digits and 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "The payment method ID is required")
    private Long paymentMethodId;

    @NotBlank(message = "The reason is required")
    @Size(max = 200, message = "The reason cannot exceed 200 characters")
    private String reason;
}
