package com.pasadita.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PrinterWebSocketHandler printerWebSocketHandler;

    public WebSocketConfig(PrinterWebSocketHandler printerWebSocketHandler) {
        this.printerWebSocketHandler = printerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(printerWebSocketHandler, "/ws/printer")
                .setAllowedOrigins("*");
    }
}
