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
public class CustomerUpdateDto {
    @NotNull(message = "The customer type ID is required")
    private Long customerTypeId;

    @NotBlank(message = "The name is required")
    @Size(min = 2, max = 150, message = "The name must be between 2 and 150 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s]+$", message = "The name can only contain letters, numbers, and spaces")
    private String name;

    @NotBlank(message = "The phone number is required")
    @Size(min = 10, max = 15, message = "The phone number must be between 10 and 15 characters")
    @Pattern(regexp = "^[0-9]+$", message = "The phone number can only contain digits")
    private String phone;

    @NotBlank(message = "The address is required")
    @Size(max = 255, message = "The address cannot exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s#.,'-]+$", message = "The address contains invalid characters")
    private String address;

    @NotBlank(message = "The city is required")
    @Size(max = 100, message = "The city cannot exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "The city can only contain letters and spaces")
    private String city;

    @NotBlank(message = "The postal code is required")
    @Size(max = 10, message = "The postal code cannot exceed 10 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s-]*$", message = "The postal code contains invalid characters")
    private String postalCode;

    @NotNull(message = "The custom discount is required")
    @DecimalMin(value = "0.0", message = "The custom discount must be a non-negative value")
    @DecimalMax(value = "100.0", message = "The custom discount must be at most 100")
    private BigDecimal customDiscount;

    @NotBlank(message = "The notes are required")
    @Size(max = 500, message = "The notes cannot exceed 500 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s.,'-]+$", message = "The notes contain invalid characters")
    private String notes;
}
