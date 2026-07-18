package com.pasadita.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasadita.api.dto.ticket.TicketResponseDto;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrinterWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 1024 * 1024;

    private final Map<String, WebSocketSession> stations = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String stationId = extractStationId(session);
        if (stationId != null) {
            WebSocketSession safeSession =
                    new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT);
            stations.put(stationId, safeSession);
            log.info("Impresora conectada con sesión concurrente segura - StationId: {}, SessionId: {}, sendTimeLimit={}ms, bufferSizeLimit={}B",
                    stationId, session.getId(), SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT);
        } else {
            log.warn("Conexión rechazada: no se proporcionó stationId. SessionId: {}", session.getId());
            try {
                session.close(CloseStatus.BAD_DATA.withReason("stationId es requerido"));
            } catch (IOException e) {
                log.error("Error al cerrar sesión sin stationId: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String stationId = extractStationId(session);
        log.info("Confirmación de impresión recibida de estación {}: {}", stationId, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String stationId = extractStationId(session);
        if (stationId != null) {
            stations.remove(stationId);
            log.info("Conexión de impresora cerrada - StationId: {}, Status: {}", stationId, status);
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        String stationId = extractStationId(session);
        log.error("Error de transporte en estación {}: {}", stationId, exception.getMessage(), exception);
        if (stationId != null) {
            stations.remove(stationId);
        }
    }

    private String extractStationId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && "stationId".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    public void sendPrintCommand(String stationId, TicketResponseDto ticket) {
        WebSocketSession session = stations.get(stationId);
        if (session != null && session.isOpen()) {
            try {
                String ticketJson = objectMapper.writeValueAsString(ticket);
                session.sendMessage(new TextMessage(ticketJson));
                log.info("Comando de impresión enviado a estación {}", stationId);
            } catch (JsonProcessingException e) {
                log.error("Error al serializar ticket para estación {}: {}", stationId, e.getMessage(), e);
            } catch (IOException e) {
                log.error("Error al enviar comando a estación {}: {}", stationId, e.getMessage(), e);
            }
        } else {
            log.warn("Estación {} no está conectada o la sesión está cerrada", stationId);
        }
    }

    public void sendOpenDrawerCommand(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            log.warn("Comando OPEN_DRAWER ignorado: stationId es nulo o vacío.");
            return;
        }

        WebSocketSession session = stations.get(stationId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", "OPEN_DRAWER",
                        "timestamp", DateTimeUtils.nowUtc().toString()
                ));
                session.sendMessage(new TextMessage(json));
                log.info("Comando OPEN_DRAWER enviado a estación {}", stationId);
            } catch (IOException e) {
                log.error("Error al enviar OPEN_DRAWER a estación {}: {}", stationId, e.getMessage(), e);
            }
        } else {
            log.warn("Estación {} no está conectada; no se pudo enviar OPEN_DRAWER", stationId);
        }
    }


    public void sendPrintCommandToAll(TicketResponseDto ticket) {
        try {
            String ticketJson = objectMapper.writeValueAsString(ticket);
            TextMessage message = new TextMessage(ticketJson);
            for (Map.Entry<String, WebSocketSession> entry : stations.entrySet()) {
                WebSocketSession session = entry.getValue();
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                        log.info("Comando de impresión enviado a la estación {}", entry.getKey());
                    } catch (IOException e) {
                        log.error("Error al enviar mensaje a estación {}: {}", entry.getKey(), e.getMessage(), e);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Error al serializar ticket: {}", e.getMessage(), e);
        }
    }

}
