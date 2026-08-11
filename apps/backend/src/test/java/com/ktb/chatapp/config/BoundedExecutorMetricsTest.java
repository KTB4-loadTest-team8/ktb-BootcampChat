package com.ktb.chatapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class BoundedExecutorMetricsTest {

    @Test
    void recordsQueueStateAndCallerRunsWhenExecutorIsSaturated() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setWaitForTasksToCompleteOnShutdown(true);

        BoundedExecutorMetrics.configure(executor, "test-executor", meterRegistry);

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        AtomicBoolean callerRan = new AtomicBoolean(false);

        executor.execute(() -> {
            taskStarted.countDown();
            await(releaseTask);
        });
        assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();

        executor.execute(() -> {
            // 실행기 큐를 점유한다.
        });
        executor.execute(() -> callerRan.set(true));

        assertThat(callerRan).isTrue();
        assertThat(meterRegistry.get("chat.executor.rejected")
                .tag("executor", "test-executor")
                .counter()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("chat.executor.caller.runs")
                .tag("executor", "test-executor")
                .counter()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("chat.executor.active")
                .tag("executor", "test-executor")
                .gauge()
                .value()).isEqualTo(1);
        assertThat(meterRegistry.get("chat.executor.queue.size")
                .tag("executor", "test-executor")
                .gauge()
                .value()).isEqualTo(1);

        releaseTask.countDown();
        executor.shutdown();
        assertThat(executor.getThreadPoolExecutor().awaitTermination(1, TimeUnit.SECONDS))
                .isTrue();

        assertThat(meterRegistry.get("chat.executor.queue.wait")
                .tag("executor", "test-executor")
                .timer()
                .count()).isEqualTo(3);
        assertThat(meterRegistry.get("chat.executor.task.duration")
                .tag("executor", "test-executor")
                .timer()
                .count()).isEqualTo(3);
        assertThat(meterRegistry.get("chat.executor.completed")
                .tag("executor", "test-executor")
                .gauge()
                .value()).isEqualTo(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 작업이 중단되었습니다.", e);
        }
    }
}
