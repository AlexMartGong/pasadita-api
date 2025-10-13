package com.pasadita.api.dto.deliveryorder;

import com.pasadita.api.enums.delivery.DeliveryStatus;
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
public class DeliveryOrderCreateDto {
    @NotNull(message = "The sale ID is required")
    private Long saleId;

    @NotNull(message = "The delivery employee ID is required")
    private Long deliveryEmployeeId;

    @NotNull(message = "The status is required")
    private DeliveryStatus status;

    @NotBlank(message = "The delivery address is required")
    @Size(max = 200, message = "The delivery address cannot exceed 200 characters")
    private String deliveryAddress;

    @NotBlank(message = "The contact phone is required")
    @Size(max = 15, message = "The contact phone cannot exceed 15 characters")
    @Pattern(regexp = "^[0-9+\\-\\s()]*$", message = "The contact phone contains invalid characters")
    private String contactPhone;

    @NotNull(message = "The delivery cost is required")
    @DecimalMin(value = "0.00", message = "The delivery cost must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "The delivery cost must have up to 10 digits and 2 decimal places")
    @Builder.Default
    private BigDecimal deliveryCost = BigDecimal.ZERO;
}
