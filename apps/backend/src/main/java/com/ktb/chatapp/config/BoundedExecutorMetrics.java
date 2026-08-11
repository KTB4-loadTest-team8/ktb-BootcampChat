package com.ktb.chatapp.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * bounded {@link ThreadPoolTaskExecutor}의 포화 상태를 Micrometer로 노출한다.
 *
 * <p>작업 실행기 설정만으로는 큐에서 기다린 시간과 작업 실행 시간을 구분하기 어렵다.
 * 이 클래스는 TaskDecorator로 두 시간을 분리하고, 실행기의 현재 상태와
 * CallerRunsPolicy 발생 횟수를 함께 기록한다.</p>
 */
public final class BoundedExecutorMetrics {

    private BoundedExecutorMetrics() {
    }

    public static void configure(
            ThreadPoolTaskExecutor executor,
            String executorName,
            MeterRegistry meterRegistry
    ) {
        Timer queueWaitTimer = Timer.builder("chat.executor.queue.wait")
                .description("Bounded executor queue wait time")
                .tag("executor", executorName)
                .publishPercentileHistogram()
                .register(meterRegistry);
        Timer taskTimer = Timer.builder("chat.executor.task.duration")
                .description("Bounded executor task execution time")
                .tag("executor", executorName)
                .publishPercentileHistogram()
                .register(meterRegistry);
        Counter rejectedCounter = Counter.builder("chat.executor.rejected")
                .description("Bounded executor rejected task count")
                .tag("executor", executorName)
                .register(meterRegistry);
        Counter callerRunsCounter = Counter.builder("chat.executor.caller.runs")
                .description("Tasks executed by the caller after executor saturation")
                .tag("executor", executorName)
                .register(meterRegistry);

        executor.setTaskDecorator(task -> {
            long submittedAt = System.nanoTime();
            return () -> {
                queueWaitTimer.record(System.nanoTime() - submittedAt, TimeUnit.NANOSECONDS);
                long startedAt = System.nanoTime();
                try {
                    task.run();
                } finally {
                    taskTimer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
                }
            };
        });
        executor.setRejectedExecutionHandler(new MetricsCallerRunsPolicy(
                rejectedCounter,
                callerRunsCounter
        ));
        executor.initialize();

        ThreadPoolExecutor delegate = executor.getThreadPoolExecutor();
        registerGauge(meterRegistry, "chat.executor.active", executorName,
                delegate, pool -> pool.getActiveCount());
        registerGauge(meterRegistry, "chat.executor.pool.size", executorName,
                delegate, pool -> pool.getPoolSize());
        registerGauge(meterRegistry, "chat.executor.queue.size", executorName,
                delegate, pool -> pool.getQueue().size());
        registerGauge(meterRegistry, "chat.executor.queue.remaining", executorName,
                delegate, pool -> pool.getQueue().remainingCapacity());
        registerGauge(meterRegistry, "chat.executor.completed", executorName,
                delegate, pool -> pool.getCompletedTaskCount());
        registerGauge(meterRegistry, "chat.executor.task.count", executorName,
                delegate, pool -> pool.getTaskCount());
    }

    private static void registerGauge(
            MeterRegistry meterRegistry,
            String metricName,
            String executorName,
            ThreadPoolExecutor executor,
            GaugeValue value
    ) {
        Gauge.builder(metricName, executor, value::get)
                .description("Bounded executor state")
                .tag("executor", executorName)
                .register(meterRegistry);
    }

    @FunctionalInterface
    private interface GaugeValue {
        double get(ThreadPoolExecutor executor);
    }

    private static final class MetricsCallerRunsPolicy
            implements java.util.concurrent.RejectedExecutionHandler {

        private final Counter rejectedCounter;
        private final Counter callerRunsCounter;
        private final java.util.concurrent.RejectedExecutionHandler delegate =
                new ThreadPoolExecutor.CallerRunsPolicy();

        private MetricsCallerRunsPolicy(Counter rejectedCounter, Counter callerRunsCounter) {
            this.rejectedCounter = rejectedCounter;
            this.callerRunsCounter = callerRunsCounter;
        }

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            rejectedCounter.increment();
            if (!executor.isShutdown()) {
                callerRunsCounter.increment();
            }
            delegate.rejectedExecution(task, executor);
        }
    }
}
