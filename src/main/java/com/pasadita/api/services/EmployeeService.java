package com.pasadita.api.services;

import com.pasadita.api.entities.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeService {

    List<Employee> findAll();

    Optional<Employee> save(Employee employee);

    boolean existsByUsername(String username);

}
