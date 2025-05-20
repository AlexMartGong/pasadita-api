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
    @Transactional
    public Optional<Employee> save(Employee employee) {
        employee.setPassword(passwordEncoder.encode(employee.getPassword()));
        return Optional.of(employeeRepository.save(employee));
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

}
