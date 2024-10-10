package org.kiwiproject.consul.util;

import static java.util.Objects.isNull;

import java.util.List;
import java.util.Optional;

public class Lists {

    private Lists() {
        // utility class
    }

    public static <T> Optional<T> firstValueOrEmpty(List<T> list) {
        return isNullOrEmpty(list) ? Optional.empty() : Optional.of(list.get(0));
    }

    public static <T> boolean isNullOrEmpty(List<T> list) {
        return isNull(list) || list.isEmpty();
    }
}
