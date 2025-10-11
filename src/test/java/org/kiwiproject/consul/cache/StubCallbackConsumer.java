package org.kiwiproject.consul.cache;

import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.kv.Value;

import java.math.BigInteger;
import java.util.List;

public class StubCallbackConsumer implements ConsulCache.CallbackConsumer<Value> {

    private final List<Value> result;
    private final String cacheHeader;
    private final String ageHeader;
    private int callCount;

    public StubCallbackConsumer(List<Value> result) {
        this(result, null, null);
    }

    public StubCallbackConsumer(List<Value> result, String cacheHeader, String ageHeader) {
        this.result = List.copyOf(result);
        this.cacheHeader = cacheHeader;
        this.ageHeader = ageHeader;
    }

    @Override
    public void consume(BigInteger index, ConsulResponseCallback<List<Value>> callback) {
        callCount++;
        callback.onComplete(new ConsulResponse<>(result, 0, true, BigInteger.ZERO, cacheHeader, ageHeader));
    }

    public int getCallCount() {
        return callCount;
    }
}
