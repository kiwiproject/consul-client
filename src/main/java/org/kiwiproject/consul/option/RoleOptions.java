package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Container for common query options used by the Consul API.
 */
@Value.Immutable
@Value.Style(jakarta = true)
public abstract class RoleOptions implements ParamAdder {

    /**
     * @deprecated for removal in 2.0.0 (replaced by {@link Options#BLANK_ROLE_OPTIONS} in 1.4.0)
     */
    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final RoleOptions BLANK = ImmutableRoleOptions.builder().build();

    public abstract Optional<String> getPolicy();
    public abstract Optional<String> getNamespace();


    @Override
    public Map<String, Object> toQuery() {
        Map<String, Object> result = new HashMap<>();

        Options.optionallyAdd(result, "policy", getPolicy());
        Options.optionallyAdd(result, "ns", getNamespace());

        return result;
    }
}
