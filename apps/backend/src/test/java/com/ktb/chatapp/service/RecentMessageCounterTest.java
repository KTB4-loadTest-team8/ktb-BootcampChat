package com.ktb.chatapp.service;

import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.repository.MessageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cache.annotation.Cacheable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecentMessageCounterTest {

    @Mock
    private MessageRepository messageRepository;

    private SimpleMeterRegistry meterRegistry;
    private RecentMessageCounter counter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        counter = new RecentMessageCounter(
                messageRepository,
                new ChatRoomMetrics(meterRegistry));
    }

    @Test
    void countRecentMessages_recordsSingleQueryDuration() {
        when(messageRepository.countRecentMessagesByRoomId(
                org.mockito.ArgumentMatchers.eq("room-1"), any(LocalDateTime.class)))
                .thenReturn(7L);

        assertThat(counter.countRecentMessages("room-1")).isEqualTo(7);
        assertThat(meterRegistry.get(ChatRoomMetrics.RECENT_COUNT_DURATION)
                .tags("status", "success", "scope", "single")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void countRecentMessages_withEmptyBatch_skipsQueryAndMetric() {
        assertThat(counter.countRecentMessages(List.of())).isEmpty();

        verifyNoInteractions(messageRepository);
        assertThat(meterRegistry.find(ChatRoomMetrics.RECENT_COUNT_DURATION).timer()).isNull();
    }

    @Test
    void countRecentMessages_singleQuery_isRedisCacheable() throws NoSuchMethodException {
        Cacheable cacheable = RecentMessageCounter.class
                .getMethod("countRecentMessages", String.class)
                .getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames())
                .containsExactly(RecentMessageCounter.RECENT_MESSAGE_COUNT_CACHE);
        assertThat(cacheable.key()).isEqualTo("#roomId");
        assertThat(cacheable.sync()).isTrue();
    }
}
