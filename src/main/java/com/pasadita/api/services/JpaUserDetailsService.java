package com.pasadita.api.services;

import com.pasadita.api.entities.Employee;
import com.pasadita.api.exceptions.EmployeeInactiveException;
import com.pasadita.api.repositories.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class JpaUserDetailsService implements UserDetailsService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Employee> employeeOptional = employeeRepository.findByUsername(username);

        if (employeeOptional.isEmpty()) {
            throw new UsernameNotFoundException("Usuario no encontrado");
        }

        Employee employee = employeeOptional.get();

        if (!employee.isActive()) {
            throw new EmployeeInactiveException("Usuario inactivo");
        }

        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(employee.getPosition().name())
        );

        return new User(
                employee.getUsername(),
                employee.getPassword(),
                employee.isActive(),
                true,
                true,
                true,
                authorities
        );

    }
}
