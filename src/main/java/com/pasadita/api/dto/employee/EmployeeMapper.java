package com.pasadita.api.dto.employee;

import com.pasadita.api.entities.Employee;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeResponseDto toResponseDto(Employee employee) {
        return EmployeeResponseDto.builder()
                .id(employee.getId())
                .fullName(employee.getFullName())
                .username(employee.getUsername())
                .position(employee.getPosition())
                .phone(employee.getPhone())
                .active(employee.isActive())
                .build();
    }

    public Employee toEntity(EmployeeCreateDto dto) {
        return Employee.builder()
                .fullName(dto.getFullName())
                .password(dto.getPassword())
                .username(dto.getUsername())
                .position(dto.getPosition())
                .phone(dto.getPhone())
                .active(dto.isActive())
                .build();
    }

    public void updateEntityFromDto(Employee employee, EmployeeUpdateDto dto) {
        employee.setFullName(dto.getFullName());
        employee.setUsername(dto.getUsername());
        employee.setPosition(dto.getPosition());
        employee.setPhone(dto.getPhone());
        employee.setActive(dto.isActive());
    }
}
