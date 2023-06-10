package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
public abstract class PutOptions implements ParamAdder {
    
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
