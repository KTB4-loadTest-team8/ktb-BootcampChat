package com.ktb.chatapp.service;

import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.repository.MessageRepository;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 채팅방 목록에 노출하는 "최근 메시지 수"의 집계 창을 한곳에서 관리한다.
 */
@Component
@RequiredArgsConstructor
public class RecentMessageCounter {

    public static final String RECENT_MESSAGE_COUNT_CACHE = "recent-message-count:v1";
    static final Duration RECENT_WINDOW = Duration.ofMinutes(30);

    private final MessageRepository messageRepository;
    private final ChatRoomMetrics chatRoomMetrics;

    /**
     * 메시지 이벤트가 집중되는 동안 동일 방의 COUNT 쿼리가 반복되지 않도록 Redis에 짧게 캐시한다.
     * RedisCacheConfig의 기본 TTL을 사용하며, 캐시 miss에서만 MongoDB COUNT를 실행한다.
     */
    @Cacheable(
            cacheNames = RECENT_MESSAGE_COUNT_CACHE,
            key = "#roomId",
            sync = true
    )
    public int countRecentMessages(String roomId) {
        Timer.Sample timerSample = chatRoomMetrics.start();
        String metricStatus = "error";
        try {
            LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
            int count = (int) messageRepository.countRecentMessagesByRoomId(roomId, since);
            metricStatus = "success";
            return count;
        } finally {
            chatRoomMetrics.recordRecentCount(timerSample, metricStatus, "single");
        }
    }

    public Map<String, Integer> countRecentMessages(Collection<String> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }

        Timer.Sample timerSample = chatRoomMetrics.start();
        String metricStatus = "error";
        try {
            LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
            Map<String, Integer> counts = messageRepository
                    .countRecentMessagesByRoomIds(roomIds, since).stream()
                    .collect(Collectors.toMap(
                            room -> room.getRoomId(),
                            room -> Math.toIntExact(room.getCount()),
                            Integer::sum
                    ));
            metricStatus = "success";
            return counts;
        } finally {
            chatRoomMetrics.recordRecentCount(timerSample, metricStatus, "batch");
        }
    }
}
