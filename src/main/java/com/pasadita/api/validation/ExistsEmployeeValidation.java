package com.pasadita.api.validation;

import com.pasadita.api.services.EmployeeService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExistsEmployeeValidation implements ConstraintValidator<ExistsEmployee, String> {

    private EmployeeService employeeService;

    @Autowired
    public ExistsEmployeeValidation(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    public ExistsEmployeeValidation() {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if (employeeService == null) {
            return true;
        }
        return !employeeService.existsByUsername(s);
    }
}
