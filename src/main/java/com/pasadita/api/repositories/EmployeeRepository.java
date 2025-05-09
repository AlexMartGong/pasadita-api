package com.pasadita.api.repositories;

import com.pasadita.api.entities.Employee;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface EmployeeRepository extends CrudRepository<Employee, Long> {

    boolean existsByUsername(String username);

    Optional<Employee> findByUsername(String username);

}
