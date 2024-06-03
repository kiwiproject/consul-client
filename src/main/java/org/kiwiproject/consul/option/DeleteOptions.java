package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
public abstract class DeleteOptions implements ParamAdder {

    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final DeleteOptions BLANK = ImmutableDeleteOptions.builder().build();

    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final DeleteOptions RECURSE = ImmutableDeleteOptions.builder().recurse(true).build();

    public abstract Optional<Long> getCas();

    public abstract Optional<Boolean> getRecurse();

    public abstract Optional<String> getDatacenter();

    @Value.Derived
    public boolean isRecurse() {
        return getRecurse().orElse(false);
    }

    @Override
    public Map<String, Object> toQuery() {
        final Map<String, Object> result = new HashMap<>();

        if (isRecurse()) {
            result.put("recurse", "");
        }

        Options.optionallyAdd(result, "cas", getCas());
        Options.optionallyAdd(result, "dc", getDatacenter());

        return result;
    }
}
