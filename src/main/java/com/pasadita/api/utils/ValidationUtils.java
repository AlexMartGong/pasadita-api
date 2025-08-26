package com.pasadita.api.utils;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.Map;

public class ValidationUtils {

    /**
     * Extract validation errors from BindingResult and return them as a Map
     * @param result the BindingResult containing validation errors
     * @return Map with field names as keys and error messages as values
     */
    public static Map<String, String> getValidationErrors(BindingResult result) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }
}
