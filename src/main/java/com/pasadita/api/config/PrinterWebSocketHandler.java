package com.pasadita.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasadita.api.dto.ticket.TicketResponseDto;
import com.pasadita.api.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler para gestionar las conexiones WebSocket de impresoras de tickets.
 * Mantiene un registro de todas las estaciones conectadas usando stationId como clave.
 * Las estaciones se conectan mediante: ws://server/ws/printer?stationId=POS1
 */
@Component
public class PrinterWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PrinterWebSocketHandler.class);

    private final Map<String, WebSocketSession> stations = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public PrinterWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String stationId = extractStationId(session);
        if (stationId != null) {
            stations.put(stationId, session);
            System.out.println("Nueva conexión de impresora establecida - StationId: " + stationId + ", SessionId: " + session.getId());
        } else {
            System.out.println("Conexión rechazada: No se proporcionó stationId. SessionId: " + session.getId());
            try {
                session.close(CloseStatus.BAD_DATA.withReason("stationId es requerido"));
            } catch (IOException e) {
                System.err.println("Error al cerrar sesión sin stationId: " + e.getMessage());
            }
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String stationId = extractStationId(session);
        String payload = message.getPayload();
        System.out.println("Confirmación de impresión recibida de estación " + stationId + ": " + payload);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String stationId = extractStationId(session);
        if (stationId != null) {
            stations.remove(stationId);
            System.out.println("Conexión de impresora cerrada - StationId: " + stationId + ", Status: " + status);
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        String stationId = extractStationId(session);
        System.err.println("Error de transporte en estación " + stationId + ": " + exception.getMessage());
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
                System.out.println("Comando de impresión enviado a estación " + stationId);
            } catch (JsonProcessingException e) {
                System.err.println("Error al serializar ticket para estación " + stationId + ": " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Error al enviar comando a estación " + stationId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Estación " + stationId + " no está conectada o la sesión está cerrada");
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
                        System.out.println("Comando de impresión enviado a estación " + entry.getKey());
                    } catch (IOException e) {
                        System.err.println("Error al enviar mensaje a estación " + entry.getKey() + ": " + e.getMessage());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Error al serializar ticket: " + e.getMessage());
        }
    }


    public int getConnectedStationsCount() {
        return stations.size();
    }


    public boolean hasConnectedStations() {
        return !stations.isEmpty();
    }

    public boolean isStationConnected(String stationId) {
        if (stationId == null) return false;
        WebSocketSession session = stations.get(stationId);
        return session != null && session.isOpen();
    }

    public Set<String> getConnectedStationIds() {
        return stations.keySet();
    }
}

