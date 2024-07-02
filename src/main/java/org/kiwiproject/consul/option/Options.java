package org.kiwiproject.consul.option;

import static java.util.Objects.nonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    static void optionallyAdd(Map<String, Object> data, String key, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<?> val) {
        val.ifPresent(value -> data.put(key, value.toString()));
    }

    public static Map<String, Object> from(ParamAdder... options) {
        Map<String, Object> result = new HashMap<>();

        for (ParamAdder adder : options) {
            if (nonNull(adder)) {
                result.putAll(adder.toQuery());
            }
        }

        return result;
    }

    static void optionallyAdd(List<String> data, String key, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Boolean> val) {
        val.ifPresent(value -> {
            if (value) {
                data.add(key);
            }
        });
    }
}
