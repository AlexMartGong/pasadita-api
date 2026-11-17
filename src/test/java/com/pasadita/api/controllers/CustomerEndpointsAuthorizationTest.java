package com.pasadita.api.controllers;

import com.pasadita.api.dto.account.CreditLimitUpdateDto;
import com.pasadita.api.services.account.CustomerAccountService;
import com.pasadita.api.services.customer.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// Runs the controllers behind real method security (no Spring Boot context, no database), so the @PreAuthorize
// expressions themselves are exercised: calling a controller directly would skip them
@SpringJUnitConfig(CustomerEndpointsAuthorizationTest.MethodSecurityConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CustomerEndpointsAuthorizationTest {

    private final CustomerAccountController customerAccountController;
    private final CustomerController customerController;
    private final CustomerAccountService customerAccountService;
    private final CustomerService customerService;

    CustomerEndpointsAuthorizationTest(CustomerAccountController customerAccountController,
                                       CustomerController customerController,
                                       CustomerAccountService customerAccountService,
                                       CustomerService customerService) {
        this.customerAccountController = customerAccountController;
        this.customerController = customerController;
        this.customerAccountService = customerAccountService;
        this.customerService = customerService;
    }

    // The context (and its mocks) is shared by every test of the class
    @AfterEach
    void clearAuthenticationAndMocks() {
        SecurityContextHolder.clearContext();
        reset(customerAccountService, customerService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_CAJERO", "ROLE_PEDIDOS"})
    void everyRoleReadsTheCustomersWithDebt(String role) {
        authenticateAs(role);

        customerAccountController.getCustomersWithDebt();

        verify(customerAccountService).findCustomersWithDebt();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_CAJERO", "ROLE_PEDIDOS"})
    void everyRoleReadsTheCustomerList(String role) {
        authenticateAs(role);

        customerController.getAllCustomers();

        verify(customerService).findAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CAJERO", "ROLE_PEDIDOS"})
    void creditLimitIsOutOfReachForCashiersAndDeliveryStaff(String role) {
        Authentication authentication = authenticateAs(role);
        CreditLimitUpdateDto creditLimit = new CreditLimitUpdateDto(new BigDecimal("5000.00"));

        assertThatThrownBy(() -> customerAccountController.updateCreditLimit(20L, creditLimit,
                new BeanPropertyBindingResult(creditLimit, "creditLimit"), authentication))
                .isInstanceOf(AuthorizationDeniedException.class);
        verifyNoInteractions(customerAccountService);
    }

    @Test
    void adminUpdatesTheCreditLimit() {
        Authentication authentication = authenticateAs("ROLE_ADMIN");
        CreditLimitUpdateDto creditLimit = new CreditLimitUpdateDto(new BigDecimal("5000.00"));

        customerAccountController.updateCreditLimit(20L, creditLimit,
                new BeanPropertyBindingResult(creditLimit, "creditLimit"), authentication);

        verify(customerAccountService).updateCreditLimit(eq(20L), eq(new BigDecimal("5000.00")), any());
    }

    // Denied before the method runs, so the arguments are never read
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CAJERO", "ROLE_PEDIDOS"})
    void customerWritesAreOutOfReachForCashiersAndDeliveryStaff(String role) {
        authenticateAs(role);

        assertThatThrownBy(() -> customerController.saveCustomer(null, null))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> customerController.updateCustomer(20L, null, null))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> customerController.changeCustomerStatus(20L, null, null))
                .isInstanceOf(AuthorizationDeniedException.class);
        verifyNoInteractions(customerService);
    }

    private static Authentication authenticateAs(String role) {
        Authentication authentication = new UsernamePasswordAuthenticationToken("empleado", null,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        CustomerAccountService customerAccountService() {
            return mock(CustomerAccountService.class);
        }

        @Bean
        CustomerService customerService() {
            return mock(CustomerService.class);
        }

        @Bean
        CustomerAccountController customerAccountController(CustomerAccountService customerAccountService) {
            return new CustomerAccountController(customerAccountService);
        }

        @Bean
        CustomerController customerController(CustomerService customerService) {
            return new CustomerController(customerService);
        }
    }
}
