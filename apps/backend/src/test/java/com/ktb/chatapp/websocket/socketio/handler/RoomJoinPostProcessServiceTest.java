package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.PARTICIPANTS_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomJoinPostProcessServiceTest {

    @Mock private SocketIOServer socketIOServer;
    @Mock private MessageRepository messageRepository;
    @Mock private MessageResponseMapper messageResponseMapper;
    @Mock private InitialMessageLoadService initialMessageLoadService;
    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private SocketIOClient client;
    @Mock private BroadcastOperations roomOperations;

    @Test
    void processAfterJoin_savesAndBroadcastsSystemMessageBeforeLoadingInitialMessages() {
        RoomJoinPostProcessService service = new RoomJoinPostProcessService(
                socketIOServer,
                messageRepository,
                messageResponseMapper,
                initialMessageLoadService,
                roomRepository,
                userRepository);
        UserResponse participant = UserResponse.builder().id("user-1").name("tester").build();
        Message savedMessage = Message.builder().id("message-1").roomId("room-1").build();
        MessageResponse response = MessageResponse.builder().id("message-1").roomId("room-1").build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(messageResponseMapper.mapToMessageResponse(savedMessage, null)).thenReturn(response);
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);
        when(roomRepository.findRoomForReadById("room-1"))
                .thenReturn(Optional.of(Room.builder()
                        .id("room-1")
                        .participantIds(Set.of("user-1"))
                        .build()));
        when(userRepository.findAllRoomSummariesById(Set.of("user-1")))
                .thenReturn(List.of(User.builder().id("user-1").name("tester").build()));

        service.processAfterJoin(client, "room-1", "user-1", "tester", List.of(participant));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message joinMessage = messageCaptor.getValue();
        assertThat(joinMessage.getRoomId()).isEqualTo("room-1");
        assertThat(joinMessage.getContent()).isEqualTo("tester님이 입장하였습니다.");
        assertThat(joinMessage.getType()).isEqualTo(MessageType.system);

        InOrder postProcessOrder = inOrder(messageRepository, roomOperations, initialMessageLoadService);
        postProcessOrder.verify(messageRepository).save(any(Message.class));
        postProcessOrder.verify(roomOperations).sendEvent(MESSAGE, response);
        postProcessOrder.verify(roomOperations).sendEvent(
                PARTICIPANTS_UPDATE,
                List.of(UserResponse.from(User.builder().id("user-1").name("tester").build())));
        postProcessOrder.verify(initialMessageLoadService).loadAndSend(client, "room-1", "user-1");
    }

    @Test
    void processAfterJoin_continuesParticipantsAndInitialLoadWhenSystemMessageSaveFails() {
        RoomJoinPostProcessService service = new RoomJoinPostProcessService(
                socketIOServer,
                messageRepository,
                messageResponseMapper,
                initialMessageLoadService,
                roomRepository,
                userRepository);
        List<UserResponse> participants = List.of(UserResponse.builder().id("user-1").build());
        when(messageRepository.save(any(Message.class))).thenThrow(new RuntimeException("MongoDB 오류"));
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);
        when(roomRepository.findRoomForReadById("room-1")).thenReturn(Optional.empty());

        service.processAfterJoin(client, "room-1", "user-1", "tester", participants);

        verify(roomOperations, never()).sendEvent(eq(MESSAGE), any());
        verify(roomOperations).sendEvent(PARTICIPANTS_UPDATE, participants);
        verify(initialMessageLoadService).loadAndSend(client, "room-1", "user-1");
    }

    @Test
    void processAfterJoin_doesNotBroadcastStaleParticipantSnapshot() {
        RoomJoinPostProcessService service = new RoomJoinPostProcessService(
                socketIOServer,
                messageRepository,
                messageResponseMapper,
                initialMessageLoadService,
                roomRepository,
                userRepository);
        List<UserResponse> staleParticipants = List.of(
                UserResponse.builder().id("user-1").name("tester").build());
        when(messageRepository.save(any(Message.class))).thenThrow(new RuntimeException("MongoDB 오류"));
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);
        when(roomRepository.findRoomForReadById("room-1"))
                .thenReturn(Optional.of(Room.builder()
                        .id("room-1")
                        .participantIds(Set.of("user-1", "user-2"))
                        .build()));
        when(userRepository.findAllRoomSummariesById(Set.of("user-1", "user-2")))
                .thenReturn(List.of(
                        User.builder().id("user-1").name("tester").build(),
                        User.builder().id("user-2").name("new participant").build()));

        service.processAfterJoin(client, "room-1", "user-1", "tester", staleParticipants);

        ArgumentCaptor<List<UserResponse>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roomOperations).sendEvent(eq(PARTICIPANTS_UPDATE), participantsCaptor.capture());
        assertThat(participantsCaptor.getValue()).containsExactlyInAnyOrder(
                UserResponse.from(User.builder().id("user-1").name("tester").build()),
                UserResponse.from(User.builder().id("user-2").name("new participant").build()));
    }
}
