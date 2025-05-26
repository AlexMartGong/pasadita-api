package com.pasadita.api.services;

import com.pasadita.api.entities.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeService {

    List<Employee> findAll();

    Optional<Employee> findById(Long id);

    Optional<Employee> save(Employee employee);

    Optional<Employee> update(Long id, Employee employee);

    boolean existsByUsername(String username);

    void deleteById(Long id);

    boolean existsById(Long id);

    Optional<Employee> findByUsername(String username);

    Optional<Employee> changePassword(Long id, Employee employee);

}
