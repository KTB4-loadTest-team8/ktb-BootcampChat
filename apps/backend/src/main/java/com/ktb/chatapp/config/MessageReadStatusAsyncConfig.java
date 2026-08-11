package com.ktb.chatapp.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 채팅방 입장 후 응답과 분리할 수 있는 작업을 위한 bounded 실행기 설정.
 *
 * <p>무제한 실행기를 사용하면 부하 상황에서 읽음 업데이트가 DB보다 빠르게 쌓일 수
 * 있으므로 풀과 대기열을 제한해 DB에 전달되는 동시성도 함께 제한한다.</p>
 */
@Configuration
@EnableAsync
public class MessageReadStatusAsyncConfig {

    private final MeterRegistry meterRegistry;

    public MessageReadStatusAsyncConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean(name = "messageReadStatusTaskExecutor")
    public Executor messageReadStatusTaskExecutor(
            @Value("${chat.message-read-status.executor.core-pool-size:2}") int corePoolSize,
            @Value("${chat.message-read-status.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${chat.message-read-status.executor.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("message-read-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        BoundedExecutorMetrics.configure(executor, "message-read-status", meterRegistry);
        return executor;
    }

    @Bean(name = "chatRoomInitialLoadTaskExecutor", destroyMethod = "close")
    public NonBlockingTaskExecutor chatRoomInitialLoadTaskExecutor(
            @Value("${chat.chat-room-initial-load.executor.core-pool-size:8}") int corePoolSize,
            @Value("${chat.chat-room-initial-load.executor.max-pool-size:16}") int maxPoolSize,
            @Value("${chat.chat-room-initial-load.executor.queue-capacity:200}") int queueCapacity
    ) {
        return nonBlockingExecutor(
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                "chat-room-load-",
                "chat-room-initial-load"
        );
    }

    @Bean(name = "chatRoomPostJoinTaskExecutor", destroyMethod = "close")
    public NonBlockingTaskExecutor chatRoomPostJoinTaskExecutor(
            @Value("${chat.room-join-post-process.executor.core-pool-size:4}") int corePoolSize,
            @Value("${chat.room-join-post-process.executor.max-pool-size:8}") int maxPoolSize,
            @Value("${chat.room-join-post-process.executor.queue-capacity:200}") int queueCapacity
    ) {
        return nonBlockingExecutor(
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                "chat-room-post-join-",
                "chat-room-post-join"
        );
    }

    @Bean(name = "chatMessageSideEffectTaskExecutor", destroyMethod = "close")
    public NonBlockingTaskExecutor chatMessageSideEffectTaskExecutor(
            @Value("${chat.chat-message-side-effect.executor.core-pool-size:2}") int corePoolSize,
            @Value("${chat.chat-message-side-effect.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${chat.chat-message-side-effect.executor.queue-capacity:100}") int queueCapacity
    ) {
        return nonBlockingExecutor(
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                "chat-message-side-effect-",
                "chat-message-side-effect"
        );
    }

    private NonBlockingTaskExecutor nonBlockingExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix,
            String metricName
    ) {
        ThreadPoolTaskExecutor primary = createExecutor(
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                threadNamePrefix
        );
        ThreadPoolTaskExecutor overflow = createExecutor(
                maxPoolSize,
                maxPoolSize,
                Math.max(queueCapacity, queueCapacity * 2),
                threadNamePrefix + "overflow-"
        );
        BoundedExecutorMetrics.configure(primary, metricName, meterRegistry, false);
        BoundedExecutorMetrics.configure(overflow, metricName + "-overflow", meterRegistry, false);
        return new NonBlockingTaskExecutor(primary, overflow);
    }

    private ThreadPoolTaskExecutor createExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
