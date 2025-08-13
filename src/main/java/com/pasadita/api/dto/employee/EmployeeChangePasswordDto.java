package com.pasadita.api.dto.employee;

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
public class EmployeeChangePasswordDto {

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8,max = 50, message = "La contraseña debe tener entre 8 y 50 caracteres")
    private String password;
}
