package com.ktb.chatapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NonBlockingTaskExecutorTest {

    @Test
    void handsOffToOverflowExecutorInsteadOfRunningOnSubmittingThread() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor primary = createExecutor("primary-");
        ThreadPoolTaskExecutor overflow = createExecutor("overflow-");
        BoundedExecutorMetrics.configure(primary, "primary", meterRegistry, false);
        BoundedExecutorMetrics.configure(overflow, "overflow", meterRegistry, false);
        NonBlockingTaskExecutor executor = new NonBlockingTaskExecutor(primary, overflow);

        CountDownLatch primaryStarted = new CountDownLatch(1);
        CountDownLatch releasePrimary = new CountDownLatch(1);
        primary.execute(() -> {
            primaryStarted.countDown();
            await(releasePrimary);
        });
        assertThat(primaryStarted.await(1, TimeUnit.SECONDS)).isTrue();

        String submittingThread = Thread.currentThread().getName();
        AtomicReference<String> executingThread = new AtomicReference<>();
        CountDownLatch overflowCompleted = new CountDownLatch(1);
        executor.execute(() -> {
            executingThread.set(Thread.currentThread().getName());
            overflowCompleted.countDown();
        });

        assertThat(overflowCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(executingThread.get()).isNotEqualTo(submittingThread);
        assertThat(meterRegistry.get("chat.executor.rejected")
                .tag("executor", "primary")
                .counter()
                .count()).isEqualTo(1);

        releasePrimary.countDown();
        executor.close();
        assertThat(primary.getThreadPoolExecutor().awaitTermination(1, TimeUnit.SECONDS))
                .isTrue();
        assertThat(overflow.getThreadPoolExecutor().awaitTermination(1, TimeUnit.SECONDS))
                .isTrue();
    }

    private static ThreadPoolTaskExecutor createExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
