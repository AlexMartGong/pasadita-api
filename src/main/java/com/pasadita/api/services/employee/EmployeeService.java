package com.pasadita.api.services.employee;

import com.pasadita.api.dto.employee.*;

import java.util.List;
import java.util.Optional;

public interface EmployeeService {

    List<EmployeeResponseDto> findAll();

    Optional<EmployeeResponseDto> findById(Long id);

    Optional<EmployeeResponseDto> save(EmployeeCreateDto employeeDto);

    Optional<EmployeeResponseDto> update(Long id, EmployeeUpdateDto employeeDto);

    boolean existsByUsername(String username);

    void deleteById(Long id);

    boolean existsById(Long id);

    Optional<EmployeeResponseDto> findByUsername(String username);

    Optional<EmployeeResponseDto> changePassword(Long id, EmployeeChangePasswordDto passwordDto);

    Optional<EmployeeResponseDto> changeStatus(Long id, EmployeeChangeStatusDto statusDto);

}
