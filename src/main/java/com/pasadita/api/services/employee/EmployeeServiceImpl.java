package com.pasadita.api.services.employee;

import com.pasadita.api.dto.employee.*;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.services.auth.AuthSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeMapper employeeMapper;
    private final AuthSessionService authSessionService;

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponseDto> findAll() {
        return ((List<Employee>) employeeRepository.findAll())
                .stream()
                .map(employeeMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeResponseDto> findById(Long id) {
        return employeeRepository.findById(id)
                .map(employeeMapper::toResponseDto);
    }

    @Override
    @Transactional
    public Optional<EmployeeResponseDto> save(EmployeeCreateDto employeeDto) {
        Employee employee = employeeMapper.toEntity(employeeDto);
        employee.setPassword(passwordEncoder.encode(employee.getPassword()));
        Employee savedEmployee = employeeRepository.save(employee);
        return Optional.of(employeeMapper.toResponseDto(savedEmployee));
    }

    @Override
    @Transactional
    public Optional<EmployeeResponseDto> update(Long id, EmployeeUpdateDto employeeDto) {
        return employeeRepository.findById(id)
                .map(existingEmployee -> {
                    employeeMapper.updateEntityFromDto(existingEmployee, employeeDto);
                    Employee savedEmployee = employeeRepository.save(existingEmployee);
                    revokeSessionsIfInactive(savedEmployee);
                    return employeeMapper.toResponseDto(savedEmployee);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return employeeRepository.existsByUsername(username);
    }

    // The employee's refresh tokens go with it: refresh_tokens.employee_id is ON DELETE CASCADE
    @Override
    @Transactional
    public void deleteById(Long id) {
        employeeRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return employeeRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeResponseDto> findByUsername(String username) {
        return employeeRepository.findByUsername(username)
                .map(employeeMapper::toResponseDto);
    }

    @Override
    @Transactional
    public Optional<EmployeeResponseDto> changePassword(Long id, EmployeeChangePasswordDto passwordDto) {
        if (passwordDto.getPassword() == null || passwordDto.getPassword().isEmpty()) {
            return Optional.empty();
        }

        return employeeRepository.findById(id)
                .map(existingEmployee -> {
                    existingEmployee.setPassword(passwordEncoder.encode(passwordDto.getPassword()));
                    Employee savedEmployee = employeeRepository.save(existingEmployee);
                    // A new password closes every open session (all devices) once their access token expires
                    authSessionService.revokeAllSessions(savedEmployee.getId());
                    return employeeMapper.toResponseDto(savedEmployee);
                });
    }

    @Override
    @Transactional
    public Optional<EmployeeResponseDto> changeStatus(Long id, EmployeeChangeStatusDto statusDto) {
        return employeeRepository.findById(id)
                .map(existingEmployee -> {
                    existingEmployee.setActive(statusDto.isActive());
                    Employee savedEmployee = employeeRepository.save(existingEmployee);
                    revokeSessionsIfInactive(savedEmployee);
                    return employeeMapper.toResponseDto(savedEmployee);
                });
    }

    // Same transaction as the status change. The access token in hand stays valid until its exp (JwtValidationFilter
    // never reads the database); refresh rejects an inactive employee anyway
    private void revokeSessionsIfInactive(Employee employee) {
        if (!employee.isActive()) {
            authSessionService.revokeAllSessions(employee.getId());
        }
    }

}
