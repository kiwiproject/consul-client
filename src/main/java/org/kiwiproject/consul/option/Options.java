package org.kiwiproject.consul.option;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides constants and utilities related to Options classes.
 */
public class Options {

    public static final DeleteOptions BLANK_DELETE_OPTIONS = ImmutableDeleteOptions.builder().build();

    public static final DeleteOptions RECURSE_DELETE_OPTIONS = ImmutableDeleteOptions.builder().recurse(true).build();

    public static final EventOptions BLANK_EVENT_OPTIONS = ImmutableEventOptions.builder().build();

    public static final PutOptions BLANK_PUT_OPTIONS = ImmutablePutOptions.builder().build();

    public static final QueryOptions BLANK_QUERY_OPTIONS = ImmutableQueryOptions.builder().build();

    public static final QueryParameterOptions BLANK_QUERY_PARAMETER_OPTIONS = ImmutableQueryParameterOptions.builder().build();

    public static final RoleOptions BLANK_ROLE_OPTIONS = ImmutableRoleOptions.builder().build();

    public static final TokenQueryOptions BLANK_TOKEN_QUERY_OPTIONS = ImmutableTokenQueryOptions.builder().build();

    public static final TransactionOptions BLANK_TRANSACTION_OPTIONS = ImmutableTransactionOptions.builder().build();

    private Options() {
        // utility class
    }

    /**
     * If the given Optional {@code val} contains a value, then a new entry is added to
     * {@code data} using {@code key} as the key and the value in {@code val} as the value.
     * <p>
     * The {@code data} Map <em>must</em> be mutable.
     *
     * @param data the map into which a new entry will be added
     * @param key the key for the new entry
     * @param val an Optional that may contain a value
     * @throws UnsupportedOperationException if {@code val} contains a value but {@code data} is an unmodifiable Map
     */
    static void optionallyAdd(Map<String, Object> data,
                              String key,
                              @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<?> val) {
        val.ifPresent(value -> data.put(key, value.toString()));
    }

    /**
     * Convert one or more options to a map.
     *
     * @param options the options to convert
     * @return a map containing the aggregated query options
     */
    public static Map<String, Object> from(ParamAdder... options) {
        checkArgument(nonNull(options), "the options vararg must not be null");
        return Arrays.stream(options)
                .filter(Objects::nonNull)
                .map(ParamAdder::toQuery)
                .flatMap(m -> m.entrySet().stream())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * If the given Optional {@code val} contains a {@code true} Boolean value, then add {@code key}
     * as a new element to {@code data}.
     * <p>
     * The {@code data} List <em>must</em> be mutable.
     *
     * @param data the list into which a new element will be added
     * @param key the value to add to the list
     * @param val a "flag" indicating whether the value of {@code key} should be added
     * @throws UnsupportedOperationException if {@code val} contains a {@code true} value
     * but {@code data} is an unmodifiable List
     */
    static void optionallyAdd(List<String> data,
                              String key,
                              @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Boolean> val) {
        val.ifPresent(value -> {
            if (value) {
                data.add(key);
            }
        });
    }
}
