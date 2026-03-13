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
 * Helper methods used by option classes to build query parameter maps and lists.
 * <p>
 * These methods are extracted here so that option classes do not need to reference {@link Options},
 * which would create a circular dependency (Options references the option classes via its constants).
 */
public class OptionHelpers {

    private OptionHelpers() {
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

    /**
     * Aggregates the query parameters from one or more {@link ParamAdder} objects into a single map
     * by calling {@link ParamAdder#toQuery()} on each non-null element and merging the results.
     * Null elements in the varargs are silently ignored.
     *
     * @param options one or more {@link ParamAdder} objects whose query parameters will be merged
     * @return a map containing the merged query parameters from all non-null options
     * @throws IllegalArgumentException if the varargs array itself is null
     */
    public static Map<String, Object> toQueryMap(ParamAdder... options) {
        checkArgument(nonNull(options), "the options vararg must not be null");
        return Arrays.stream(options)
                .filter(Objects::nonNull)
                .map(ParamAdder::toQuery)
                .flatMap(m -> m.entrySet().stream())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
