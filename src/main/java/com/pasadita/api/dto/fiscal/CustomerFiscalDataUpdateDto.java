package com.pasadita.api.dto.fiscal;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFiscalDataUpdateDto {

    @NotBlank(message = "The RFC is required")
    @Pattern(
            regexp = "^([A-ZÑ&]{3,4})\\d{6}([A-Z\\d]{3})$",
            message = "The RFC is not valid (must be 12 or 13 alphanumeric characters)"
    )
    private String rfc;

    @NotBlank(message = "The razon social is required")
    @Size(max = 150, message = "The razon social cannot exceed 150 characters")
    private String razonSocial;

    @NotBlank(message = "The regimen fiscal is required")
    @Pattern(regexp = "^\\d{3}$", message = "The regimen fiscal must be a 3-digit SAT code")
    private String regimenFiscal;

    @NotBlank(message = "The fiscal postal code is required")
    @Pattern(regexp = "^\\d{5}$", message = "The fiscal postal code must be 5 digits")
    private String codigoPostalFiscal;

    @NotBlank(message = "The uso CFDI is required")
    @Size(min = 3, max = 4, message = "The uso CFDI must be 3 or 4 characters")
    private String usoCfdi;

    @NotBlank(message = "The billing email is required")
    @Email(message = "The billing email is not a valid email address")
    @Size(max = 100, message = "The billing email cannot exceed 100 characters")
    private String emailFacturacion;

    @NotNull(message = "The active flag is required")
    private Boolean active;

    @Size(max = 15, message = "The phone cannot exceed 15 characters")
    @Pattern(regexp = "^[0-9]*$", message = "The phone can only contain digits")
    private String phone;

    @Size(max = 200, message = "The address cannot exceed 200 characters")
    private String address;

}
