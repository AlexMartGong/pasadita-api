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
public class CustomerTypeCreateDto {
    @NotNull(message = "ID is required for update")
    private Long id;

    @NotBlank(message = "Customer type name is required")
    @Size(min = 2, max = 100, message = "Customer type name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s]+$", message = "Customer type name can only contain letters, numbers, and spaces")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s.,'-]+$", message = "Description contains invalid characters")
    private String description;

    @NotNull(message = "Discount percentage is required")
    @DecimalMin(value = "0.0", message = "Discount must be a non-negative value")
    @DecimalMax(value = "100.0", message = "Discount cannot exceed 100")
    private BigDecimal discount;
}
