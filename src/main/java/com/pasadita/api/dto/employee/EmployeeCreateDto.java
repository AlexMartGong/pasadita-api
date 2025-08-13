package com.pasadita.api.dto.employee;

import com.pasadita.api.enums.user.Position;
import com.pasadita.api.validation.ExistsEmployee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Size(min = 5, max = 150, message = "El nombre completo debe tener entre 5 y 150 caracteres")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s]+$", message = "El nombre completo solo puede contener letras y espacios")
    private String fullName;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 50, message = "La contraseña debe tener entre 8 y 50 caracteres")
    private String password;

    @ExistsEmployee
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 4, max = 50, message = "El nombre de usuario debe tener entre 4 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
            message = "El nombre de usuario solo puede contener letras, números, puntos, guiones bajos y guiones")
    private String username;

    @NotNull(message = "La posición es obligatoria")
    private Position position;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(min = 10, max = 15, message = "El teléfono debe tener entre 10 y 15 caracteres")
    @Pattern(regexp = "^[0-9]+$", message = "El teléfono solo puede contener números")
    private String phone;

    @NotNull(message = "El estado activo es obligatorio")
    private boolean active = true;
}
