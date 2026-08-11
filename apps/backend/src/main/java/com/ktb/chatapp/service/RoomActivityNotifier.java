package com.ktb.chatapp.service;

import com.ktb.chatapp.event.RoomActivityEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 새 메시지가 저장되면 채팅방 목록의 활성도 지표를 갱신하도록 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomActivityNotifier {

    private final RecentMessageCounter recentMessageCounter;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public void notifyMessageStored(String roomId) {
        notifyMessageStoredInternal(roomId);
    }

    /**
     * 메시지 Socket 처리 경로와 분리해 채팅방 목록의 활성도를 갱신한다.
     */
    @Async("chatMessageSideEffectTaskExecutor")
    public void notifyMessageStoredAsync(String roomId) {
        notifyMessageStoredInternal(roomId);
    }

    private void notifyMessageStoredInternal(String roomId) {
        if (roomId == null) {
            return;
        }

        Timer.Sample timerSample = Timer.start(meterRegistry);
        String status = "error";
        try {
            int recentMessageCount = recentMessageCounter.countRecentMessages(roomId);
            eventPublisher.publishEvent(new RoomActivityEvent(this, roomId, recentMessageCount));
            status = "success";
        } catch (Exception e) {
            Counter.builder("chat.messages.side.effect.errors")
                    .description("Message side-effect failure count")
                    .tag("operation", "room_activity")
                    .register(meterRegistry)
                    .increment();
            log.error("roomActivity 이벤트 발행 실패: roomId={}", roomId, e);
        } finally {
            timerSample.stop(Timer.builder("chat.messages.side.effect.duration")
                    .description("Message side-effect processing time")
                    .tag("operation", "room_activity")
                    .tag("status", status)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }
}
