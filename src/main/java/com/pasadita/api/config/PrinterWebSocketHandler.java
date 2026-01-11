package com.pasadita.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasadita.api.dto.ticket.TicketResponseDto;
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

    /**
     * Extrae el stationId del query parameter de la URL de conexión.
     * Ejemplo URL: ws://server/ws/printer?stationId=POS1
     *
     * @param session Sesión WebSocket
     * @return stationId o null si no se encuentra
     */
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

    /**
     * Envía un comando de impresión a una estación específica.
     *
     * @param stationId ID de la estación (ej: POS1)
     * @param ticket    DTO del ticket a imprimir
     * @return true si el mensaje fue enviado exitosamente
     */
    public boolean sendPrintCommand(String stationId, TicketResponseDto ticket) {
        WebSocketSession session = stations.get(stationId);
        if (session != null && session.isOpen()) {
            try {
                String ticketJson = objectMapper.writeValueAsString(ticket);
                session.sendMessage(new TextMessage(ticketJson));
                System.out.println("Comando de impresión enviado a estación " + stationId);
                return true;
            } catch (JsonProcessingException e) {
                System.err.println("Error al serializar ticket para estación " + stationId + ": " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Error al enviar comando a estación " + stationId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Estación " + stationId + " no está conectada o la sesión está cerrada");
        }
        return false;
    }

    /**
     * Envía un comando de impresión a todas las estaciones conectadas.
     *
     * @param ticket DTO del ticket a imprimir
     */
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

    /**
     * Obtiene el número de estaciones conectadas.
     *
     * @return Número de estaciones activas
     */
    public int getConnectedStationsCount() {
        return stations.size();
    }

    /**
     * Verifica si hay al menos una estación conectada.
     *
     * @return true si hay al menos una estación conectada
     */
    public boolean hasConnectedStations() {
        return !stations.isEmpty();
    }

    /**
     * Verifica si una estación específica está conectada.
     *
     * @param stationId ID de la estación
     * @return true si la estación está conectada
     */
    public boolean isStationConnected(String stationId) {
        WebSocketSession session = stations.get(stationId);
        return session != null && session.isOpen();
    }

    /**
     * Obtiene los IDs de todas las estaciones conectadas.
     *
     * @return Set con los IDs de las estaciones
     */
    public Set<String> getConnectedStationIds() {
        return stations.keySet();
    }
}

