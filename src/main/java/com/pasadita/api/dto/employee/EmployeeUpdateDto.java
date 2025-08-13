package com.pasadita.api.dto.employee;

import com.pasadita.api.enums.user.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeUpdateDto {
    
    @NotBlank(message = "El nombre completo es obligatorio")
    private String fullName;
    
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;
    
    @NotNull(message = "La posición es obligatoria")
    private Position position;
    
    @NotBlank(message = "El teléfono es obligatorio")
    private String phone;
    
    private boolean active;
}
