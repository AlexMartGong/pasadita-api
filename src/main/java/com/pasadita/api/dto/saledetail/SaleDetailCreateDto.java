package com.pasadita.api.dto.saledetail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleDetailCreateDto {
    @NotNull(message = "Sale ID is required")
    private Long saleId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be positive")
    @Digits(integer = 10, fraction = 3, message = "Quantity must have up to 10 digits and 3 decimal places")
    private BigDecimal quantity;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", message = "Unit price must be positive")
    @Digits(integer = 10, fraction = 2, message = "Unit price must have up to 10 digits and 2 decimal places")
    private BigDecimal unitPrice;

    @NotNull(message = "Subtotal is required")
    @DecimalMin(value = "0.00", message = "Subtotal must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "Subtotal must have up to 12 digits and 2 decimal places")
    private BigDecimal subtotal;

    @DecimalMin(value = "0.00", message = "Discount must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "Discount must have up to 10 digits and 2 decimal places")
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @NotNull(message = "Total is required")
    @DecimalMin(value = "0.00", message = "Total must be non-negative")
    @Digits(integer = 12, fraction = 2, message = "Total must have up to 12 digits and 2 decimal places")
    private BigDecimal total;
}
