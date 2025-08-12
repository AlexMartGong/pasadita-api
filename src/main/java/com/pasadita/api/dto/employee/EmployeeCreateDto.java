package com.pasadita.api.dto.employee;

import com.pasadita.api.enums.user.Position;
import com.pasadita.api.validation.ExistsEmployee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCreateDto {

    @NotBlank(message = "El nombre completo es obligatorio")
    private String fullName;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    @ExistsEmployee
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    @NotNull(message = "La posición es obligatoria")
    private Position position;

    @NotBlank(message = "El teléfono es obligatorio")
    private String phone;

    private boolean active = true;
}
