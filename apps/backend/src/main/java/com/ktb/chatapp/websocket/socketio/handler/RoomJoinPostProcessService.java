package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

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
            socketIOServer.getRoomOperations(roomId)
                    .sendEvent(PARTICIPANTS_UPDATE, loadLatestParticipants(roomId, participants));
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

    /**
     * 후처리는 방 입장 이벤트보다 늦게 실행될 수 있으므로, 입장 시점에 캡처한 참가자
     * 목록을 그대로 브로드캐스트하지 않는다. 동시 입장 중 오래된 스냅샷이 최신 목록을
     * 덮어쓰는 race를 막기 위해 전송 직전에 현재 방 상태를 다시 읽는다.
     */
    private List<UserResponse> loadLatestParticipants(
            String roomId,
            List<UserResponse> fallbackParticipants
    ) {
        try {
            return roomRepository.findRoomForReadById(roomId)
                    .map(room -> {
                        if (room.getParticipantIds() == null || room.getParticipantIds().isEmpty()) {
                            return List.<UserResponse>of();
                        }

                        Map<String, UserResponse> usersById = new HashMap<>();
                        userRepository.findAllRoomSummariesById(room.getParticipantIds())
                                .forEach(user -> usersById.put(user.getId(), UserResponse.from(user)));
                        return room.getParticipantIds().stream()
                                .map(usersById::get)
                                .filter(java.util.Objects::nonNull)
                                .toList();
                    })
                    .orElse(fallbackParticipants);
        } catch (Exception e) {
            log.warn("현재 참가자 목록 조회 실패, 입장 시점 스냅샷을 사용합니다. roomId={}", roomId, e);
            return fallbackParticipants;
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
