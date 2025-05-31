package com.pasadita.api.services;

import com.pasadita.api.entities.Employee;
import com.pasadita.api.repositories.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<Employee> findAll() {
        return (List<Employee>) employeeRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Employee> findById(Long id) {
        return employeeRepository.findById(id);
    }

    @Override
    @Transactional
    public Optional<Employee> save(Employee employee) {
        employee.setPassword(passwordEncoder.encode(employee.getPassword()));
        return Optional.of(employeeRepository.save(employee));
    }

    @Override
    @Transactional
    public Optional<Employee> update(Long id, Employee employee) {
        return employeeRepository.findById(id)
                .map(existingEmployee -> {
                    existingEmployee.setFullName(employee.getFullName());
                    existingEmployee.setUsername(employee.getUsername());
                    existingEmployee.setPosition(employee.getPosition());
                    existingEmployee.setPhone(employee.getPhone());
                    existingEmployee.setActive(employee.isActive());

                    if (employee.getPassword() != null && !employee.getPassword().isEmpty()) {
                        existingEmployee.setPassword(passwordEncoder.encode(employee.getPassword()));
                    }

                    return employeeRepository.save(existingEmployee);
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
    public Optional<Employee> findByUsername(String username) {
        return employeeRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public Optional<Employee> changePassword(Long id, Employee employee) {

        if (employee.getPassword() == null || employee.getPassword().isEmpty()) {
            return Optional.empty();
        }

        return employeeRepository.findById(id)
                .map(existingEmployee -> {
                    existingEmployee.setPassword(passwordEncoder.encode(employee.getPassword()));
                    return Optional.of(employeeRepository.save(existingEmployee));
                })
                .orElse(Optional.empty());
    }

    @Override
    @Transactional
    public Optional<Employee> changeStatus(Long id, Employee employee) {
        return employeeRepository.findById(id)
                .map(existingEmployee -> {
                    existingEmployee.setActive(employee.isActive());
                    return employeeRepository.save(existingEmployee);
                });
    }

}
