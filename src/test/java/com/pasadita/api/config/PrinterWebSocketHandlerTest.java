package com.pasadita.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrinterWebSocketHandlerTest {

    private static final String STATION_ID = "caja-1";
    private static final Instant T0 = Instant.parse("2026-10-14T18:00:00Z");

    @Mock private Clock clock;

    private PrinterWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(T0);
        handler = new PrinterWebSocketHandler(new JsonMapper(), clock);
    }

    private static WebSocketSession openSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/ws/printer?stationId=" + STATION_ID));
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void lateCloseOfReplacedSessionKeepsTheNewSession() throws Exception {
        WebSocketSession oldSession = openSession("old");
        WebSocketSession newSession = openSession("new");

        handler.afterConnectionEstablished(oldSession);
        handler.afterConnectionEstablished(newSession);
        handler.afterConnectionClosed(oldSession, CloseStatus.GOING_AWAY);

        assertThat(handler.sendOpenDrawerCommand(STATION_ID)).isTrue();
        verify(newSession).sendMessage(argThat(message ->
                message instanceof TextMessage text && text.getPayload().contains("\"OPEN_DRAWER\"")));
        verify(oldSession, never()).sendMessage(any());
    }

    @Test
    void closingTheCurrentSessionRemovesIt() throws Exception {
        WebSocketSession session = openSession("current");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(handler.sendOpenDrawerCommand(STATION_ID)).isFalse();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void newConnectionForSameStationClosesThePreviousSession() throws Exception {
        WebSocketSession oldSession = openSession("old");
        WebSocketSession newSession = openSession("new");

        handler.afterConnectionEstablished(oldSession);
        handler.afterConnectionEstablished(newSession);

        verify(oldSession).close(argThat(status -> status.getCode() == CloseStatus.NORMAL.getCode()));
        verify(newSession, never()).close(any());
    }

    @Test
    void sendingToASessionThatIsNoLongerOpenReportsFailureAndCleansItUp() throws Exception {
        WebSocketSession session = openSession("stale");
        handler.afterConnectionEstablished(session);

        when(session.isOpen()).thenReturn(false);
        assertThat(handler.sendOpenDrawerCommand(STATION_ID)).isFalse();

        // Even if the session reported open again, the entry is already gone
        lenient().when(session.isOpen()).thenReturn(true);
        assertThat(handler.sendOpenDrawerCommand(STATION_ID)).isFalse();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void heartbeatPingsSessionsThatAnsweredRecently() throws Exception {
        WebSocketSession session = openSession("alive");
        handler.afterConnectionEstablished(session);

        when(clock.instant()).thenReturn(T0.plusSeconds(60));
        handler.handleMessage(session, new PongMessage());
        when(clock.instant()).thenReturn(T0.plusSeconds(100));
        handler.sendHeartbeats();

        verify(session).sendMessage(isA(PingMessage.class));
        verify(session, never()).close(any());
        assertThat(handler.sendOpenDrawerCommand(STATION_ID)).isTrue();
    }

    @Test
    void heartbeatEvictsSessionsWithoutPongForTooLong() throws Exception {
        WebSocketSession session = openSession("silent");
        handler.afterConnectionEstablished(session);

        when(clock.instant()).thenReturn(T0.plus(PrinterWebSocketHandler.HEARTBEAT_TIMEOUT).plusSeconds(1));
        handler.sendHeartbeats();

        verify(session).close(argThat(status -> status.getCode() == CloseStatus.SESSION_NOT_RELIABLE.getCode()));
        assertThat(handler.sendOpenDrawerCommand(STATION_ID)).isFalse();
    }
}
