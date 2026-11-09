package com.pasadita.api.exceptions;

import com.pasadita.api.entities.SaleDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    // Goes through Spring MVC's handler resolution, so it also proves the catch-all no longer wins
    @Test
    void preAuthorizeDenialIsReportedAsForbiddenInsteadOfServerError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DeniedController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You don't have permission to perform this action"));
    }

    @RestController
    static class DeniedController {

        // What Spring Security 7 method security throws when @PreAuthorize rejects the caller
        @GetMapping("/denied")
        void denied() {
            throw new AuthorizationDeniedException("Access Denied");
        }
    }
}
