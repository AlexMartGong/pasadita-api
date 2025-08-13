package com.pasadita.api.dto.employee;

import com.pasadita.api.enums.user.Position;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponseDto {
    private Long id;
    private String fullName;
    private String username;
    private Position position;
    private String phone;
    private boolean active;
}
