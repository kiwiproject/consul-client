package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
public abstract class PutOptions implements ParamAdder {

    /**
     * @deprecated for removal in 2.0.0 (replaced by {@link Options#BLANK_PUT_OPTIONS} in 1.4.0)
     */
    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final PutOptions BLANK = ImmutablePutOptions.builder().build();

    public abstract Optional<Long> getCas();
    public abstract Optional<String> getAcquire();
    public abstract Optional<String> getRelease();
    public abstract Optional<String> getDc();
    public abstract Optional<String> getToken();

    @Override
    public final Map<String, Object> toQuery() {
        Map<String, Object> result = new HashMap<>();

        Options.optionallyAdd(result, "dc", getDc());
        Options.optionallyAdd(result, "cas", getCas());
        Options.optionallyAdd(result, "acquire", getAcquire());
        Options.optionallyAdd(result, "release", getRelease());
        Options.optionallyAdd(result, "token", getToken());

        return result;
    }
}
