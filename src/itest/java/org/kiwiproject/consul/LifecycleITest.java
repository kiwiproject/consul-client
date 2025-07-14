package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import okhttp3.ConnectionPool;
import okhttp3.internal.Util;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class LifecycleITest extends BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LifecycleITest.class);

    @Test
    void shouldBeDestroyable() {
        Consul client = Consul.builder().withHostAndPort(defaultClientHostAndPort).build();
        assertThat(client.isDestroyed()).isFalse();

        client.destroy();

        assertThat(client.isDestroyed()).isTrue();
    }

    @Test
    void shouldDestroyTheExecutorServiceWhenDestroyMethodIsInvoked() {
        var connectionPool = spy(new ConnectionPool());
        var executorService = mock(ExecutorService.class);

        Consul client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withExecutorService(executorService)
                .withConnectionPool(connectionPool)
                .build();

        client.destroy();

        assertThat(client.isDestroyed()).isTrue();
        verify(executorService).shutdownNow();
        verify(connectionPool).evictAll();
    }

    @Test
    void shouldBeDestroyableWithCustomExecutorService() throws InterruptedException {
        var connectionPool = new ConnectionPool();
        var workQueue = new SynchronousQueue<Runnable>();
        var threadFactory = Util.threadFactory("OkHttp Dispatcher", false);
        var executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                workQueue, threadFactory);

        executorService.execute(() -> {
            var currentThread = Thread.currentThread();
            LOG.info("This is a Task printing a message in Thread {}", currentThread);
        });

        Consul client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withExecutorService(executorService)
                .withConnectionPool(connectionPool)
                .build();

        client.destroy();

        assertThat(client.isDestroyed()).isTrue();

        boolean wasTerminated = executorService.awaitTermination(1, TimeUnit.SECONDS);
        assertThat(wasTerminated).isTrue();
    }
}
