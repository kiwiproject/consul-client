package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Container for common transaction options used by the Consul API.
 */
@Value.Immutable
@Value.Style(jakarta = true)
public abstract class TransactionOptions implements ParamAdder {

    /**
     * @deprecated for removal in 2.0.0 (replaced by {@link Options#BLANK_TRANSACTION_OPTIONS} in 1.4.0)
     */
    @Deprecated(since = "1.3.3", forRemoval = true)
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
