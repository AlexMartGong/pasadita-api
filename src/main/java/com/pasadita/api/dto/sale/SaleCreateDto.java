package com.pasadita.api.dto.sale;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleCreateDto {
    @NotNull(message = "The employee ID is required")
    private Long employeeId;

    @NotNull(message = "The customer ID is required")
    private Long customerId;

    @NotNull(message = "The payment method ID is required")
    private Long paymentMethodId;

    @NotNull(message = "The datetime is required")
    @PastOrPresent(message = "The datetime cannot be in the future")
    private LocalDateTime datetime;

    @NotNull(message = "The subtotal is required")
    @DecimalMin(value = "0.00", message = "The subtotal must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "The subtotal must have up to 12 digits and 2 decimal places")
    private BigDecimal subtotal;

    @NotNull(message = "The discount amount is required")
    @DecimalMin(value = "0.00", message = "The discount amount must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "The discount amount must have up to 10 digits and 2 decimal places")
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull(message = "The total is required")
    @DecimalMin(value = "0.00", message = "The total must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "The total must have up to 12 digits and 2 decimal places")
    private BigDecimal total;

    @NotNull(message = "The paid status is required")
    @Builder.Default
    private Boolean paid = false;

    @Size(max = 500, message = "The notes cannot exceed 500 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s.,'-]*$", message = "The notes contain invalid characters")
    private String notes;
}