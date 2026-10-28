package com.pasadita.api.controllers;

import com.pasadita.api.dto.account.DeliveryCollectionDto;
import com.pasadita.api.dto.account.PaymentActor;
import com.pasadita.api.services.account.CustomerAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerAccountControllerTest {

    @Mock private CustomerAccountService customerAccountService;

    private CustomerAccountController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerAccountController(customerAccountService);
    }

    // The body has no employee field: whoever is authenticated is the one recording the collection
    @Test
    void collectionIsRecordedByTheAuthenticatedUser() {
        DeliveryCollectionDto collection = DeliveryCollectionDto.builder()
                .amountReceived(new BigDecimal("400")).paymentMethodId(1L).build();

        controller.registerDeliveryCollection(200L, collection,
                new BeanPropertyBindingResult(collection, "collection"),
                new UsernamePasswordAuthenticationToken("repartidor1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_PEDIDOS"))));

        verify(customerAccountService).registerDeliveryCollection(200L, collection,
                new PaymentActor("repartidor1", false));
    }
}
