package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.ktb.chatapp.dto.JoinRoomSuccessResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import io.micrometer.core.instrument.Timer;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 방 입장 처리 핸들러
 * 채팅방 입장, 참가자 관리, 초기 메시지 로드 담당
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RoomJoinHandler {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final UserRooms userRooms;
    private final RoomJoinPostProcessService roomJoinPostProcessService;
    private final ChatRoomMetrics chatRoomMetrics;
    
    @OnEvent(JOIN_ROOM)
    public void handleJoinRoom(SocketIOClient client, String roomId) {
        Timer.Sample timerSample = chatRoomMetrics.start();
        String metricStatus = "error";
        try {
            String userId = getUserId(client);
            String userName = getUserName(client);

            if (userId == null) {
                metricStatus = "unauthorized";
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "Unauthorized"));
                return;
            }
            
            // SocketUser는 AuthTokenListenerImpl에서 JWT와 사용자 존재를 검증한 뒤 주입된다.
            // 여기서 같은 사용자를 다시 단건 조회하지 않고, 참가자 응답을 만들 때의 batch 조회를 재사용한다.
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null) {
                metricStatus = "room_not_found";
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "채팅방을 찾을 수 없습니다."));
                return;
            }
            
            // 이미 해당 방에 참여 중인지 확인
            if (userRooms.isInRoom(userId, roomId)) {
                log.debug("User {} already in room {}", userId, roomId);
                client.joinRoom(roomId);
                client.sendEvent(JOIN_ROOM_SUCCESS, Map.of("roomId", roomId));
                metricStatus = "already_joined";
                return;
            }

            // 참가자 추가 결과로 반환된 최신 Room을 재사용해 입장 후 재조회하지 않는다.
            room = roomRepository.addParticipantAndReturn(roomId, userId).orElse(null);
            if (room == null) {
                metricStatus = "room_not_found";
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "채팅방을 찾을 수 없습니다."));
                return;
            }

            // Join socket room and add to user's room set
            client.joinRoom(roomId);
            userRooms.add(userId, roomId);

            List<UserResponse> participants = getParticipantResponses(room);
            
            JoinRoomSuccessResponse response = JoinRoomSuccessResponse.builder()
                .roomId(roomId)
                .participants(participants)
                .messages(Collections.emptyList())
                .hasMore(false)
                .activeStreams(Collections.emptyList())
                .initialMessagesPending(true)
                .build();

            client.sendEvent(JOIN_ROOM_SUCCESS, response);

            // ACK 이후 부수 작업에서 시스템 메시지를 저장·브로드캐스트한 뒤 초기 메시지를 조회한다.
            roomJoinPostProcessService.processAfterJoin(client, roomId, userId, userName, participants);

            log.info("User {} joined room {} successfully. Initial messages loading asynchronously",
                userName, roomId);
            metricStatus = "success";

        } catch (Exception e) {
            log.error("Error handling joinRoom", e);
            client.sendEvent(JOIN_ROOM_ERROR, Map.of(
                "message", e.getMessage() != null ? e.getMessage() : "채팅방 입장에 실패했습니다."
            ));
        } finally {
            chatRoomMetrics.recordRoomJoin(timerSample, metricStatus);
        }
    }
    
    private SocketUser getUser(SocketIOClient client) {
        return client.get("user");
    }

    private String getUserId(SocketIOClient client) {
        SocketUser user = getUser(client);
        return user != null ? user.id() : null;
    }

    private String getUserName(SocketIOClient client) {
        SocketUser user = getUser(client);
        return user != null ? user.name() : null;
    }

    private List<UserResponse> getParticipantResponses(Room room) {
        if (room.getParticipantIds() == null || room.getParticipantIds().isEmpty()) {
            return List.of();
        }

        Map<String, User> usersById = new HashMap<>();
        userRepository.findAllById(room.getParticipantIds())
                .forEach(user -> usersById.put(user.getId(), user));

        return room.getParticipantIds().stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(UserResponse::from)
                .toList();
    }
}
