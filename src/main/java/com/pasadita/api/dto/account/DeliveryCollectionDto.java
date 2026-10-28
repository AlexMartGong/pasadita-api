package com.pasadita.api.dto.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
public class DeliveryCollectionDto {
    @NotNull(message = "The amount received is required")
    @DecimalMin(value = "0.00", message = "The amount received must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "The amount received must have up to 10 digits and 2 decimal places")
    private BigDecimal amountReceived;

    // Required only when amountReceived > 0 (checked in the service)
    private Long paymentMethodId;

    @Size(max = 36, message = "The client request ID cannot exceed 36 characters")
    private String clientRequestId;
}
