package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.PARTICIPANTS_UPDATE;

/**
 * 채팅방 입장 성공 응답 이후 실행할 시스템 메시지 및 참가자 알림 작업을 처리한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RoomJoinPostProcessService {

    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final MessageResponseMapper messageResponseMapper;
    private final InitialMessageLoadService initialMessageLoadService;

    @Async("chatRoomPostJoinTaskExecutor")
    public void processAfterJoin(
            SocketIOClient client,
            String roomId,
            String userId,
            String userName,
            List<UserResponse> participants
    ) {
        try {
            Message savedJoinMessage = messageRepository.save(createJoinMessage(roomId, userName));
            socketIOServer.getRoomOperations(roomId)
                    .sendEvent(MESSAGE, messageResponseMapper.mapToMessageResponse(savedJoinMessage, null));
        } catch (Exception e) {
            log.error("Error saving or broadcasting join system message for room {}", roomId, e);
        }

        try {
            socketIOServer.getRoomOperations(roomId).sendEvent(PARTICIPANTS_UPDATE, participants);
        } catch (Exception e) {
            log.error("Error broadcasting participants after join for room {}", roomId, e);
        }

        try {
            // 시스템 입장 메시지 저장 이후 조회를 시작해 초기 목록과 실시간 이벤트의 중복만 허용한다.
            initialMessageLoadService.loadAndSend(client, roomId, userId);
        } catch (Exception e) {
            log.error("Error scheduling initial message load after join for room {}", roomId, e);
        }
    }

    private Message createJoinMessage(String roomId, String userName) {
        return Message.builder()
                .roomId(roomId)
                .content(userName + "님이 입장하였습니다.")
                .type(MessageType.system)
                .timestamp(LocalDateTime.now())
                .mentions(new ArrayList<>())
                .reactions(new HashMap<>())
                .readers(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
    }
}
