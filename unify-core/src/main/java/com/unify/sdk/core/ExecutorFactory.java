package com.unify.sdk.core;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认线程池：在 Java 21+ 上使用虚拟线程，低版本退回有界的平台线程池。
 */
final class ExecutorFactory {

    private ExecutorFactory() {
    }

    static ExecutorService createDefault() {
        try {
            Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            return (ExecutorService) method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return createPlatform();
        }
    }

    static ExecutorService createPlatform() {
        int size = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "unify-sdk-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(size, size, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(4096), threadFactory, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
