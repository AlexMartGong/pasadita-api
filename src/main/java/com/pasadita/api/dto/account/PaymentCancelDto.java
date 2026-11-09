package com.pasadita.api.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCancelDto {
    @NotBlank(message = "The reason is required")
    @Size(max = 200, message = "The reason cannot exceed 200 characters")
    private String reason;
}
