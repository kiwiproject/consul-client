package org.kiwiproject.consul.option;

import static org.kiwiproject.consul.option.Options.optionallyAdd;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
public abstract class EventOptions implements ParamAdder {

    /**
     * @deprecated for removal in 2.0.0 (replaced by {@link Options#BLANK_EVENT_OPTIONS} in 1.4.0)
     */
    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final EventOptions BLANK = ImmutableEventOptions.builder().build();

    public abstract Optional<String> getDatacenter();
    public abstract Optional<String> getNodeFilter();
    public abstract Optional<String> getServiceFilter();
    public abstract Optional<String> getTagFilter();

    @Override
    public Map<String, Object> toQuery() {
        Map<String, Object> result = new HashMap<>();

        optionallyAdd(result, "node", getNodeFilter());
        optionallyAdd(result, "service", getServiceFilter());
        optionallyAdd(result, "tag", getTagFilter());
        optionallyAdd(result, "dc", getDatacenter());

        return result;
    }
}
