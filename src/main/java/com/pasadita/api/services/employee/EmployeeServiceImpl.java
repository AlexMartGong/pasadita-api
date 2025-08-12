package com.pasadita.api.services.employee;

import com.pasadita.api.dto.employee.*;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.repositories.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmployeeMapper employeeMapper;

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
                    return employeeMapper.toResponseDto(savedEmployee);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return employeeRepository.existsByUsername(username);
    }

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
                    return employeeMapper.toResponseDto(savedEmployee);
                });
    }

}
