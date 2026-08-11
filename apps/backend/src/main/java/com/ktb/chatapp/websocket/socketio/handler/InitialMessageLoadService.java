package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ERROR;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.PREVIOUS_MESSAGES_LOADED;

/**
 * 채팅방 입장 성공 응답 이후 초기 메시지를 로드하고 해당 소켓에 전달한다.
 *
 * <p>초기 메시지 조회는 MongoDB 조회와 사용자/파일 정보 조합을 포함하므로
 * Socket.IO 이벤트 핸들러의 응답 경로에서 실행하지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialMessageLoadService {

    private static final int INITIAL_MESSAGE_LIMIT = 30;

    private final MessageLoader messageLoader;

    @Async("chatRoomInitialLoadTaskExecutor")
    public void loadAndSend(SocketIOClient client, String roomId, String userId) {
        try {
            FetchMessagesRequest request = new FetchMessagesRequest(
                    roomId,
                    INITIAL_MESSAGE_LIMIT,
                    null
            );
            FetchMessagesResponse response = messageLoader.loadMessagesOrThrow(request, userId);
            client.sendEvent(PREVIOUS_MESSAGES_LOADED, response);
        } catch (Exception e) {
            log.error("Error loading initial messages asynchronously for room {}", roomId, e);
            client.sendEvent(ERROR, Map.of(
                    "code", "LOAD_ERROR",
                    "roomId", roomId,
                    "message", "초기 메시지를 불러오는 중 오류가 발생했습니다."
            ));
        }
    }
}
