package com.pasadita.api.exceptions;

import com.pasadita.api.entities.SaleDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void persistTimeConstraintViolationIsReportedAsBadRequestWithFieldMessages() {
        SaleDetail invalidDetail = SaleDetail.builder().quantity(new BigDecimal("0.0065")).build();
        ConstraintViolationException exception;
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            exception = new ConstraintViolationException(
                    validatorFactory.getValidator().validateProperty(invalidDetail, "quantity"));
        }

        ResponseEntity<Map<String, String>> response = handler.handleConstraintViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsExactly(Map.entry("quantity", "Quantity must have up to 10 digits and 3 decimal places"));
    }
}
