package com.pasadita.api.config;

import com.pasadita.api.dto.ticket.TicketResponseDto;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrinterWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 1024 * 1024;
    static final long HEARTBEAT_INTERVAL_MS = 25_000;
    static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(75);
    private static final String PRINT_RESULT_TYPE = "PRINT_RESULT";

    // Values are the ConcurrentWebSocketSessionDecorator; its getId() delegates to the raw session id
    private final Map<String, WebSocketSession> stations = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSeenBySessionId = new ConcurrentHashMap<>();
    private final JsonMapper objectMapper;
    private final Clock clock;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String stationId = extractStationId(session);
        if (stationId == null) {
            log.warn("Conexión rechazada: no se proporcionó stationId. SessionId: {}", session.getId());
            closeQuietly(session, CloseStatus.BAD_DATA.withReason("stationId es requerido"));
            return;
        }

        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT);
        lastSeenBySessionId.put(session.getId(), clock.instant());
        // put before closing the previous one, so its close callback already finds the new session and keeps it
        WebSocketSession previous = stations.put(stationId, safeSession);
        log.info("Impresora conectada - StationId: {}, SessionId: {}, Remote: {}",
                stationId, session.getId(), session.getRemoteAddress());

        if (previous != null && !previous.getId().equals(session.getId())) {
            log.warn("Sesión reemplazada - StationId: {}, SessionId anterior: {}, SessionId nueva: {}. Cerrando la anterior",
                    stationId, previous.getId(), session.getId());
            lastSeenBySessionId.remove(previous.getId());
            closeQuietly(previous, CloseStatus.NORMAL.withReason("Reemplazada por una nueva conexión"));
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        markAlive(session);
        String stationId = extractStationId(session);
        String payload = message.getPayload();
        try {
            JsonNode json = objectMapper.readTree(payload);
            if (PRINT_RESULT_TYPE.equals(json.path("type").asString())) {
                if (json.path("success").isBoolean() && !json.path("success").asBoolean()) {
                    log.warn("PRINT_RESULT fallido - StationId: {}, SessionId: {}, Payload: {}",
                            stationId, session.getId(), payload);
                } else {
                    log.info("PRINT_RESULT - StationId: {}, SessionId: {}, Payload: {}",
                            stationId, session.getId(), payload);
                }
                return;
            }
            log.info("Mensaje recibido de estación {} (SessionId: {}): {}", stationId, session.getId(), payload);
        } catch (JacksonException e) {
            log.warn("Mensaje no JSON recibido de estación {} (SessionId: {}): {}", stationId, session.getId(), payload);
        }
    }

    @Override
    protected void handlePongMessage(@NonNull WebSocketSession session, @NonNull PongMessage message) {
        markAlive(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        lastSeenBySessionId.remove(session.getId());
        String stationId = extractStationId(session);
        if (stationId == null) {
            return;
        }
        boolean removed = removeIfCurrent(stationId, session.getId());
        WebSocketSession current = stations.get(stationId);
        if (!removed && current != null) {
            log.info("Conexión obsoleta cerrada, se conserva la vigente - StationId: {}, SessionId: {}, SessionId vigente: {}, Status: {}",
                    stationId, session.getId(), current.getId(), status);
        } else {
            log.info("Conexión de impresora cerrada - StationId: {}, SessionId: {}, Status: {}",
                    stationId, session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        String stationId = extractStationId(session);
        log.error("Error de transporte - StationId: {}, SessionId: {}: {}",
                stationId, session.getId(), exception.toString(), exception);
        if (stationId != null) {
            removeIfCurrent(stationId, session.getId());
        }
    }

    public boolean sendPrintCommand(String stationId, TicketResponseDto ticket) {
        WebSocketSession session = stations.get(stationId);
        if (session == null) {
            log.warn("Estación {} no está conectada; no se pudo enviar el ticket", stationId);
            return false;
        }
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(ticket));
            return deliver(stationId, session, message, "ticket");
        } catch (JacksonException e) {
            log.error("Error al serializar ticket para estación {}: {}", stationId, e.getMessage(), e);
            return false;
        }
    }

    public boolean sendOpenDrawerCommand(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            log.warn("Comando OPEN_DRAWER ignorado: stationId es nulo o vacío.");
            return false;
        }

        WebSocketSession session = stations.get(stationId);
        if (session == null) {
            log.warn("Estación {} no está conectada; no se pudo enviar OPEN_DRAWER", stationId);
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "OPEN_DRAWER",
                    "timestamp", DateTimeUtils.nowUtc().toString()
            ));
            return deliver(stationId, session, new TextMessage(json), "OPEN_DRAWER");
        } catch (JacksonException e) {
            log.error("Error al serializar OPEN_DRAWER para estación {}: {}", stationId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * @return number of stations whose open session accepted the ticket
     */
    public int sendPrintCommandToAll(TicketResponseDto ticket) {
        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(ticket));
        } catch (JacksonException e) {
            log.error("Error al serializar ticket: {}", e.getMessage(), e);
            return 0;
        }
        int delivered = 0;
        for (Map.Entry<String, WebSocketSession> entry : stations.entrySet()) {
            if (deliver(entry.getKey(), entry.getValue(), message, "ticket")) {
                delivered++;
            }
        }
        return delivered;
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeats() {
        Instant now = clock.instant();
        for (Map.Entry<String, WebSocketSession> entry : stations.entrySet()) {
            String stationId = entry.getKey();
            WebSocketSession session = entry.getValue();
            Instant lastSeen = lastSeenBySessionId.computeIfAbsent(session.getId(), id -> now);
            Duration silence = Duration.between(lastSeen, now);
            if (silence.compareTo(HEARTBEAT_TIMEOUT) > 0) {
                log.warn("Sesión desalojada por heartbeat - StationId: {}, SessionId: {}, sin pong desde hace {} s",
                        stationId, session.getId(), silence.toSeconds());
                evict(stationId, session, CloseStatus.SESSION_NOT_RELIABLE.withReason("Sin respuesta al heartbeat"));
            } else {
                deliver(stationId, session, new PingMessage(), "PING");
            }
        }
    }

    /**
     * Sends a message to a station session.
     *
     * @return {@code true} when the message was written to an open session; a closed or failing session is
     * evicted under the identity rule and {@code false} is returned
     */
    private boolean deliver(String stationId, WebSocketSession session, WebSocketMessage<?> message, String command) {
        if (!session.isOpen()) {
            log.warn("Sesión de estación {} ya no está abierta (SessionId: {}); no se pudo enviar {}",
                    stationId, session.getId(), command);
            removeIfCurrent(stationId, session.getId());
            lastSeenBySessionId.remove(session.getId());
            return false;
        }
        try {
            session.sendMessage(message);
            if (!(message instanceof PingMessage)) {
                log.info("Comando {} enviado a estación {} (SessionId: {})", command, stationId, session.getId());
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("Error al enviar {} a estación {} (SessionId: {}): {}",
                    command, stationId, session.getId(), e.getMessage(), e);
            evict(stationId, session, CloseStatus.SESSION_NOT_RELIABLE);
            return false;
        }
    }

    private void evict(String stationId, WebSocketSession session, CloseStatus status) {
        removeIfCurrent(stationId, session.getId());
        lastSeenBySessionId.remove(session.getId());
        closeQuietly(session, status);
    }

    /**
     * Removes the station entry only while it still points to the given session, so a late close of a replaced
     * session never drops the session that replaced it.
     */
    private boolean removeIfCurrent(String stationId, String sessionId) {
        AtomicBoolean removed = new AtomicBoolean(false);
        stations.computeIfPresent(stationId, (key, current) -> {
            if (current.getId().equals(sessionId)) {
                removed.set(true);
                return null;
            }
            return current;
        });
        return removed.get();
    }

    private void markAlive(WebSocketSession session) {
        lastSeenBySessionId.computeIfPresent(session.getId(), (id, previous) -> clock.instant());
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException e) {
            log.debug("Error al cerrar sesión {}: {}", session.getId(), e.getMessage());
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

}
