package com.pasadita.api.controllers;

import com.pasadita.api.config.PrinterWebSocketHandler;
import com.pasadita.api.services.sale.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleControllerTest {

    @Mock private SaleService saleService;
    @Mock private PrinterWebSocketHandler printerWebSocketHandler;

    private SaleController controller;

    @BeforeEach
    void setUp() {
        controller = new SaleController(saleService, printerWebSocketHandler);
    }

    @Test
    void openDrawerReturns503WhenStationIsNotConnected() {
        when(printerWebSocketHandler.sendOpenDrawerCommand("caja-1")).thenReturn(false);

        ResponseEntity<?> response = controller.openDrawer("caja-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).asInstanceOf(MAP)
                .containsEntry("success", false)
                .extractingByKey("error").asString().contains("caja-1", "no está conectada");
    }

    @Test
    void openDrawerReturns400WhenStationIdIsMissing() {
        ResponseEntity<?> response = controller.openDrawer(" ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(printerWebSocketHandler);
    }

    @Test
    void openDrawerReturns200WhenCommandIsDelivered() {
        when(printerWebSocketHandler.sendOpenDrawerCommand("caja-1")).thenReturn(true);

        ResponseEntity<?> response = controller.openDrawer("caja-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).asInstanceOf(MAP).containsEntry("success", true);
    }
}
