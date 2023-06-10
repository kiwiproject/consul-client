package org.kiwiproject.consul.option;

import static java.util.Objects.nonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Options {
    private Options(){}

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
