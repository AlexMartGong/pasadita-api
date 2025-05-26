package com.pasadita.api.exceptions;

import org.springframework.security.core.AuthenticationException;

public class EmployeeInactiveException extends AuthenticationException {

    public EmployeeInactiveException(String msg) {
        super(msg);
    }

    public EmployeeInactiveException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
