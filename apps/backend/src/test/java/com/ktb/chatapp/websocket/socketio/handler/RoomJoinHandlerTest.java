package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.ktb.chatapp.dto.JoinRoomSuccessResponse;
import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.JOIN_ROOM_ERROR;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.JOIN_ROOM_SUCCESS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomJoinHandlerTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRooms userRooms;
    @Mock private RoomJoinPostProcessService roomJoinPostProcessService;
    @Mock private SocketIOClient client;

    private RoomJoinHandler handler;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new RoomJoinHandler(
                roomRepository,
                userRepository,
                userRooms,
                roomJoinPostProcessService,
                new ChatRoomMetrics(meterRegistry));
    }

    @Test
    void handleJoinRoom_rejectsUnauthorizedClient() {
        when(client.get("user")).thenReturn(null);

        handler.handleJoinRoom(client, "room-1");

        verify(client).sendEvent(eq(JOIN_ROOM_ERROR), any());
        org.assertj.core.api.Assertions.assertThat(meterRegistry
                .get(ChatRoomMetrics.ROOM_JOIN_DURATION)
                .tag("status", "unauthorized")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void handleJoinRoom_sendsSuccessBeforeSchedulingPostJoinProcessing() {
        SocketUser socketUser = new SocketUser("user-1", "tester", "session-1", "socket-1");
        User user = User.builder().id("user-1").name("tester").email("tester@example.com").build();
        User participant = User.builder().id("user-2").name("participant").email("participant@example.com").build();
        Room room = Room.builder().id("room-1").name("room").participantIds(Set.of("user-1", "user-2")).build();
        when(client.get("user")).thenReturn(socketUser);
        when(userRepository.findAllById(Set.of("user-1", "user-2"))).thenReturn(List.of(user, participant));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomRepository.addParticipantAndReturn("room-1", "user-1"))
                .thenReturn(Optional.of(room));
        when(userRooms.isInRoom("user-1", "room-1")).thenReturn(false);
        handler.handleJoinRoom(client, "room-1");

        verify(roomRepository).addParticipantAndReturn("room-1", "user-1");
        verify(roomRepository, times(1)).findById("room-1");
        verify(client).joinRoom("room-1");
        verify(userRooms).add("user-1", "room-1");
        ArgumentCaptor<JoinRoomSuccessResponse> responseCaptor =
                ArgumentCaptor.forClass(JoinRoomSuccessResponse.class);
        verify(client).sendEvent(eq(JOIN_ROOM_SUCCESS), responseCaptor.capture());
        JoinRoomSuccessResponse response = responseCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(response.getMessages()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(response.isHasMore()).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.isInitialMessagesPending()).isTrue();

        InOrder joinResponseThenPostProcess = inOrder(client, roomJoinPostProcessService);
        joinResponseThenPostProcess.verify(client).sendEvent(eq(JOIN_ROOM_SUCCESS), any());
        joinResponseThenPostProcess.verify(roomJoinPostProcessService)
                .processAfterJoin(eq(client), eq("room-1"), eq("user-1"), eq("tester"), any());
        verify(userRepository).findAllById(Set.of("user-1", "user-2"));
        verify(userRepository, never()).findById("user-1");
        verify(userRepository, never()).findById("user-2");
        org.assertj.core.api.Assertions.assertThat(meterRegistry
                .get(ChatRoomMetrics.ROOM_JOIN_DURATION)
                .tag("status", "success")
                .timer()
                .count()).isEqualTo(1);
    }
}
