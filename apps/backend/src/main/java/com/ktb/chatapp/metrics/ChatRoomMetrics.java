package com.ktb.chatapp.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채팅방 진입 경로의 저카디널리티 성능 지표를 한곳에서 관리한다.
 *
 * <p>roomId, userId 같은 고유값은 태그로 사용하지 않는다. Prometheus에서는
 * publishPercentileHistogram으로 생성되는 bucket을 이용해 인스턴스 전체의
 * p50/p95/p99를 집계한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatRoomMetrics {

    public static final String ROOM_DETAIL_DURATION = "chat.room.detail.duration";
    public static final String SOCKET_AUTH_DURATION = "chat.socket.auth.duration";
    public static final String ROOM_JOIN_DURATION = "chat.room.join.duration";
    public static final String MESSAGE_LOAD_DURATION = "chat.messages.load.duration";
    public static final String READ_UPDATE_DURATION = "chat.messages.read.update.duration";
    public static final String RECENT_COUNT_DURATION = "chat.messages.recent.count.duration";

    private final MeterRegistry meterRegistry;

    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    public void recordRoomDetail(Timer.Sample sample, String status) {
        stop(sample, ROOM_DETAIL_DURATION, "Room detail request processing time",
                "status", status);
    }

    public void recordSocketAuth(Timer.Sample sample, String status) {
        stop(sample, SOCKET_AUTH_DURATION, "Socket.IO authentication and connection setup time",
                "status", status);
    }

    public void recordRoomJoin(Timer.Sample sample, String status) {
        stop(sample, ROOM_JOIN_DURATION, "Socket.IO room join processing time",
                "status", status);
    }

    public void recordMessageLoad(Timer.Sample sample, String status, String loadType) {
        stop(sample, MESSAGE_LOAD_DURATION, "Chat message load processing time",
                "status", status,
                "load_type", loadType);
    }

    public void recordReadUpdate(Timer.Sample sample, String status) {
        stop(sample, READ_UPDATE_DURATION, "Message read status update time",
                "status", status);
    }

    public void recordRecentCount(Timer.Sample sample, String status, String scope) {
        stop(sample, RECENT_COUNT_DURATION, "Recent message count query time",
                "status", status,
                "scope", scope);
    }

    private void stop(
            Timer.Sample sample,
            String metricName,
            String description,
            String... tags
    ) {
        sample.stop(Timer.builder(metricName)
                .description(description)
                .tags(tags)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }
}
