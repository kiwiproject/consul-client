package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Container for common transaction options used by the Consul API.
 */
@Value.Immutable
public abstract class TransactionOptions implements ParamAdder {

    public static final TransactionOptions BLANK = ImmutableTransactionOptions.builder().build();

    public abstract Optional<String> getDatacenter();

    @Value.Default
    public ConsistencyMode getConsistencyMode() {
        return ConsistencyMode.DEFAULT;
    }

    @Override
    public Map<String, Object> toQuery() {
        Map<String, Object> result = new HashMap<>();

        Optional<String> consistencyMode = getConsistencyMode().toParam();
        consistencyMode.ifPresent(s -> result.put(s, "true"));

        Options.optionallyAdd(result, "dc", getDatacenter());

        return result;
    }

    @Override
    public Map<String, String> toHeaders() {
        var consistencyMode = getConsistencyMode();
        return new HashMap<>(consistencyMode.getAdditionalHeaders());
    }
}
