package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ERROR;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.PREVIOUS_MESSAGES_LOADED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialMessageLoadServiceTest {

    @Mock private MessageLoader messageLoader;
    @Mock private SocketIOClient client;

    @Test
    void loadAndSend_loadsThirtyInitialMessagesAndSendsExistingEvent() {
        InitialMessageLoadService service = new InitialMessageLoadService(messageLoader);
        FetchMessagesResponse response = FetchMessagesResponse.builder()
                .messages(List.of())
                .hasMore(false)
                .build();
        when(messageLoader.loadMessagesOrThrow(any(FetchMessagesRequest.class), eq("user-1")))
                .thenReturn(response);

        service.loadAndSend(client, "room-1", "user-1");

        ArgumentCaptor<FetchMessagesRequest> requestCaptor =
                ArgumentCaptor.forClass(FetchMessagesRequest.class);
        verify(messageLoader).loadMessagesOrThrow(requestCaptor.capture(), eq("user-1"));
        FetchMessagesRequest request = requestCaptor.getValue();
        assertThat(request.roomId()).isEqualTo("room-1");
        assertThat(request.limit()).isEqualTo(30);
        assertThat(request.before()).isNull();
        verify(client).sendEvent(PREVIOUS_MESSAGES_LOADED, response);
        verify(client, never()).sendEvent(eq(ERROR), any());
    }

    @Test
    void loadAndSend_sendsLoadErrorWhenInitialMessageQueryFails() {
        InitialMessageLoadService service = new InitialMessageLoadService(messageLoader);
        when(messageLoader.loadMessagesOrThrow(any(FetchMessagesRequest.class), eq("user-1")))
                .thenThrow(new RuntimeException("MongoDB 오류"));

        service.loadAndSend(client, "room-1", "user-1");

        ArgumentCaptor<Map<String, String>> errorCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).sendEvent(eq(ERROR), errorCaptor.capture());
        assertThat(errorCaptor.getValue())
                .containsEntry("code", "LOAD_ERROR")
                .containsEntry("message", "초기 메시지를 불러오는 중 오류가 발생했습니다.");
        verify(client, never()).sendEvent(eq(PREVIOUS_MESSAGES_LOADED), any());
    }
}
