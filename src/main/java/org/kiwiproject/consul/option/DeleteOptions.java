package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
public abstract class DeleteOptions implements ParamAdder {

    /**
     * @deprecated for removal in 2.0.0 (replaced by {@link Options#BLANK_DELETE_OPTIONS} in 1.4.0)
     */
    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final DeleteOptions BLANK = ImmutableDeleteOptions.builder().build();

    /**
     * @deprecated for removal in 2.0.0 (replaced by {@link Options#RECURSE_DELETE_OPTIONS} in 1.4.0)
     */
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
