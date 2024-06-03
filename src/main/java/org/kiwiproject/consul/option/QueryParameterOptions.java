package org.kiwiproject.consul.option;

import static org.kiwiproject.consul.option.Options.optionallyAdd;

import org.immutables.value.Value;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Container for common query options used by the Consul API.
 */
@Value.Immutable
@Value.Style(jakarta = true)
public abstract class QueryParameterOptions implements ParamAdder {

    @Deprecated(since = "1.3.3", forRemoval = true)
    public static final QueryParameterOptions BLANK = ImmutableQueryParameterOptions.builder().build();

    public abstract Optional<Boolean> getReplaceExistingChecks();
    public abstract Optional<Boolean> getPrune();

    @Override
    public List<String> toQueryParameters() {
        List<String> result = new LinkedList<>();

        optionallyAdd(result, "replace-existing-checks", getReplaceExistingChecks());
        optionallyAdd(result, "prune", getPrune());

        return result;
    }
}
