package com.ktb.chatapp.config;

import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 작업 제출자가 Socket.IO 이벤트 스레드여도 포화 시 caller-runs를 하지 않는 실행기.
 *
 * <p>기본 실행기가 포화되면 별도의 bounded overflow 실행기로 넘긴다. 두 실행기가
 * 모두 포화된 경우에만 제출을 거부해, 이벤트 스레드가 DB 작업을 직접 수행하지 않도록
 * 한다.</p>
 */
@RequiredArgsConstructor
public final class NonBlockingTaskExecutor implements AsyncTaskExecutor, AutoCloseable {

    private final ThreadPoolTaskExecutor primary;
    private final ThreadPoolTaskExecutor overflow;

    @Override
    public void execute(Runnable task) {
        try {
            primary.execute(task);
        } catch (RejectedExecutionException primaryRejected) {
            try {
                overflow.execute(task);
            } catch (RejectedExecutionException overflowRejected) {
                overflowRejected.addSuppressed(primaryRejected);
                throw overflowRejected;
            }
        }
    }

    @Override
    public void execute(Runnable task, long startTimeout) {
        execute(task);
    }

    @Override
    public void close() {
        primary.shutdown();
        overflow.shutdown();
    }
}
