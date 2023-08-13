package org.kiwiproject.consul.cache;

import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.kv.Value;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AsyncCallbackConsumer implements ConsulCache.CallbackConsumer<Value>, AutoCloseable {
    private final List<Value> result;
    private final ExecutorService executor;
    private int callCount;

    public AsyncCallbackConsumer(List<Value> result) {
        this.result = List.copyOf(result);
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public void consume(BigInteger index, final ConsulResponseCallback<List<Value>> callback) {
        callCount++;
        executor.submit(() ->
                callback.onComplete(new ConsulResponse<>(result, 0, true, BigInteger.ZERO, null, null)));
    }

    public int getCallCount() {
        return callCount;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            var terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (! terminated) {
                throw new RuntimeException("Executor timed out after 5 seconds before all tasks terminated");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Executor did not terminate within timeout", e);
        }
    }
}
