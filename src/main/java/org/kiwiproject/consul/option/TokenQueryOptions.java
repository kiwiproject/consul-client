package org.kiwiproject.consul.option;

import org.immutables.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Container for token query options used by the Consul ACL API.
 */
@Value.Immutable
@Value.Style(jakarta = true)
public abstract class TokenQueryOptions implements ParamAdder {

    /**
     * @deprecated for removal in 2.0.0 (replaced by {@link Options#BLANK_TOKEN_QUERY_OPTIONS} in 1.4.0)
     */
    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final TokenQueryOptions BLANK = ImmutableTokenQueryOptions.builder().build();

    public abstract Optional<String> getPolicy();
    public abstract Optional<String> getRole();
    public abstract Optional<String> getAuthMethod();
    public abstract Optional<String> getAuthMethodNamespace();
    public abstract Optional<String> getNamespace();

    @Override
    public Map<String, Object> toQuery() {
        Map<String, Object> result = new HashMap<>();

        Options.optionallyAdd(result, "policy", getPolicy());
        Options.optionallyAdd(result, "role", getRole());
        Options.optionallyAdd(result, "authmethod", getAuthMethod());
        Options.optionallyAdd(result, "authmethod-ns", getAuthMethodNamespace());
        Options.optionallyAdd(result, "ns", getNamespace());

        return result;
    }
}
