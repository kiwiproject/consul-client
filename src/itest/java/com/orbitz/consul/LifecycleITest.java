package com.orbitz.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.internal.Util;

class LifecycleITest extends BaseIntegrationTest {

    @Test
    void shouldBeDestroyable() {
        Consul client = Consul.builder().withHostAndPort(defaultClientHostAndPort).build();
        assertThat(client.isDestroyed()).isFalse();

        client.destroy();

        assertThat(client.isDestroyed()).isTrue();
    }

    @Test
    void shouldDestroyTheExecutorServiceWhenDestroyMethodIsInvoked() throws InterruptedException {
        var connectionPool = new ConnectionPool();
        var executorService = mock(ExecutorService.class);

        Consul client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withExecutorService(executorService)
                .withConnectionPool(connectionPool)
                .build();

        client.destroy();

        verify(executorService).shutdownNow();
        verifyNoMoreInteractions(executorService);
    }

    @Test
    void shouldBeDestroyableWithCustomExecutorService() throws InterruptedException {
        var connectionPool = new ConnectionPool();
        var executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", false));

        executorService.execute(() -> {
            Thread currentThread = Thread.currentThread();
            System.out.println("This is a Task printing a message in Thread " + currentThread);
        });
        Consul client = Consul.builder().withHostAndPort(defaultClientHostAndPort)
            .withExecutorService(executorService).withConnectionPool(connectionPool).build();
        client.destroy();
        assertThat(client.isDestroyed()).isTrue();
    }

    // TODO What to do with this? Delete it, move to documentation. convert it to a test (of something)?
    public static void main(String[] args) {
        var connectionPool = new ConnectionPool();
        var executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 10, TimeUnit.SECONDS,
                new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", false));

        // execute a task in order to force the creation of a Thread in the ThreadPool
        executorService.execute(() -> {
            Thread currentThread = Thread.currentThread();
            System.out.println("This is a Task printing a message in Thread " + currentThread);
        });

        Consul client = Consul.builder().withHostAndPort(defaultClientHostAndPort)
            .withExecutorService(executorService).withConnectionPool(connectionPool).build();

        // do not destroy the Consul client
        // in order to verify that the Java VM does not terminate, and waits for some time (keepAliveTime parameter of the ThreadPoolExecutor)
        // if we call destroy() then the JVM terminates immediately

        //client.destroy();
    }

}
