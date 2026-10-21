package com.pasadita.api.entities;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate re-runs these Bean Validation constraints at persist time, so a value the entity rejects
 * fails the whole sale even when the request DTO accepted it.
 */
class SaleDetailValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private Set<ConstraintViolation<SaleDetail>> validateQuantity(String quantity) {
        SaleDetail saleDetail = SaleDetail.builder().quantity(new BigDecimal(quantity)).build();
        return validator.validateProperty(saleDetail, "quantity");
    }

    @Test
    void gramFractionOfAKiloIsAccepted() {
        assertThat(validateQuantity("0.006")).isEmpty();
    }

    @Test
    void smallestQuantityTheColumnCanStoreIsAccepted() {
        assertThat(validateQuantity("0.001")).isEmpty();
    }

    @Test
    void zeroQuantityIsRejected() {
        assertThat(validateQuantity("0"))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Quantity must be positive");
    }

    @Test
    void quantityBeyondColumnScaleIsRejected() {
        assertThat(validateQuantity("0.0065"))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Quantity must have up to 10 digits and 3 decimal places");
    }
}
