package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.MessageReadStatusService;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageLoader {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageResponseMapper messageResponseMapper;
    private final MessageReadStatusService messageReadStatusService;
    private final ChatRoomMetrics chatRoomMetrics;

    private static final int BATCH_SIZE = 30;

    /**
     * 메시지 로드
     */
    public FetchMessagesResponse loadMessages(FetchMessagesRequest data, String userId) {
        Timer.Sample timerSample = chatRoomMetrics.start();
        String metricStatus = "error";
        String loadType = data.before() == null ? "initial" : "history";
        try {
            FetchMessagesResponse response = loadMessagesInternal(
                    data.roomId(), data.limit(BATCH_SIZE), data.before(LocalDateTime.now()), userId);
            metricStatus = "success";
            return response;
        } catch (Exception e) {
            log.error("Error loading initial messages for room {}", data.roomId(), e);
            return FetchMessagesResponse.builder()
                    .messages(emptyList())
                    .hasMore(false)
                    .build();
        } finally {
            chatRoomMetrics.recordMessageLoad(timerSample, metricStatus, loadType);
        }
    }

    private FetchMessagesResponse loadMessagesInternal(
            String roomId,
            int limit,
            LocalDateTime before,
            String userId) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("timestamp").descending());

        Slice<Message> messagePage =
                messageRepository.findByRoomIdAndTimestampBefore(roomId, before, pageable);

        List<Message> messages = messagePage.getContent();

        // DESC로 조회했으므로 ASC로 재정렬 (채팅 UI 표시 순서)
        List<Message> sortedMessages = messages.reversed();
        
        var messageIds = sortedMessages.stream().map(Message::getId).toList();
        // 읽음 상태 저장은 메시지 응답을 막지 않도록 bounded async executor로 분리한다.
        messageReadStatusService.updateReadStatusAsync(messageIds, userId);
        
        Map<String, User> usersById = findUsersById(sortedMessages);

        // 메시지 응답 생성
        List<MessageResponse> messageResponses = messageResponseMapper
                .mapToMessageResponses(sortedMessages, usersById);

        boolean hasMore = messagePage.hasNext();

        log.debug("Messages loaded - roomId: {}, limit: {}, count: {}, hasMore: {}",
                roomId, limit, messageResponses.size(), hasMore);

        return FetchMessagesResponse.builder()
                .messages(messageResponses)
                .hasMore(hasMore)
                .build();
    }

    private Map<String, User> findUsersById(List<Message> messages) {
        Set<String> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (senderIds.isEmpty()) {
            return Map.of();
        }

        Map<String, User> usersById = new HashMap<>();
        userRepository.findAllById(senderIds)
                .forEach(user -> usersById.put(user.getId(), user));
        return usersById;
    }
}
