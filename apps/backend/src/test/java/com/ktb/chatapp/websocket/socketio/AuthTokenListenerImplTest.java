package com.ktb.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.JwtService;
import com.ktb.chatapp.service.SessionService;
import com.ktb.chatapp.service.SessionValidationResult;
import com.ktb.chatapp.websocket.socketio.handler.ConnectionLoginHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenListenerImplTest {

    @Mock private JwtService jwtService;
    @Mock private SessionService sessionService;
    @Mock private UserRepository userRepository;
    @Mock private ObjectProvider<ConnectionLoginHandler> handlerProvider;
    @Mock private ConnectionLoginHandler connectionLoginHandler;
    @Mock private SocketIOClient client;

    private SimpleMeterRegistry meterRegistry;
    private AuthTokenListenerImpl listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new AuthTokenListenerImpl(
                jwtService,
                sessionService,
                userRepository,
                handlerProvider,
                new ChatRoomMetrics(meterRegistry));
    }

    @Test
    void getAuthTokenResult_recordsSuccessfulSocketAuthentication() {
        User user = User.builder()
                .id("user-1")
                .name("tester")
                .build();
        when(jwtService.extractUserId("token-1")).thenReturn("user-1");
        when(sessionService.validateSession("user-1", "session-1"))
                .thenReturn(SessionValidationResult.valid(null));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(client.getSessionId()).thenReturn(UUID.randomUUID());
        when(handlerProvider.getObject()).thenReturn(connectionLoginHandler);

        assertThat(listener.getAuthTokenResult(
                Map.of("token", "token-1", "sessionId", "session-1"), client))
                .isNotNull();

        verify(connectionLoginHandler).onConnect(any(SocketIOClient.class), any(SocketUser.class));
        assertThat(meterRegistry.get(ChatRoomMetrics.SOCKET_AUTH_DURATION)
                .tag("status", "success")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void getAuthTokenResult_recordsMissingCredentials() {
        assertThat(listener.getAuthTokenResult(Map.of(), client)).isNotNull();

        assertThat(meterRegistry.get(ChatRoomMetrics.SOCKET_AUTH_DURATION)
                .tag("status", "missing_credentials")
                .timer()
                .count()).isEqualTo(1);
    }
}
