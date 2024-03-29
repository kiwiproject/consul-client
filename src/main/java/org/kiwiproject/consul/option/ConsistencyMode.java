package org.kiwiproject.consul.option;

import java.util.Map;
import java.util.Optional;

public class ConsistencyMode {
    public static final ConsistencyMode DEFAULT = new ConsistencyMode("DEFAULT", 0, null);
    public static final ConsistencyMode STALE = new ConsistencyMode("STALE", 1, "stale");
    public static final ConsistencyMode CONSISTENT = new ConsistencyMode("CONSISTENT", 2, "consistent");

    private final String name;
    private final int ordinal;
    private final String param;
    private final Map<String, String> additionalHeaders;

    private ConsistencyMode(String name, int ordinal, String param) {
        this(name, ordinal, param, Map.of());
    }

    private ConsistencyMode(String name, int ordinal, String param, Map<String, String> headers) {
        this.name = name;
        this.ordinal = ordinal;
        this.param = param;
        this.additionalHeaders = headers;
    }

    public final Optional<String> toParam() {
        return Optional.ofNullable(param);
    }

    /**
     * Get the Additional HTTP headers to add to request.
     *
     * @return a not null but possibly empty map
     */
    public final Map<String, String> getAdditionalHeaders() {
        return additionalHeaders;
    }

    /**
     * Creates a cached Consistency.
     *
     * @param maxAgeInSeconds   Optional duration in seconds after which data is
     *                          considered too old
     * @param maxStaleInSeconds Optional duration for which data can be late
     *                          compared to Consul Server leader.
     * @return a not null ConsistencyMode
     * @see <a href="https://developer.hashicorp.com/consul/api-docs/features/caching#simple-caching">Simple Caching</a>
     */
    public static ConsistencyMode createCachedConsistencyWithMaxAgeAndStale(Optional<Long> maxAgeInSeconds,
                                                                            Optional<Long> maxStaleInSeconds) {
        String maxAge = "";
        if (maxAgeInSeconds.isPresent()) {
            long v = maxAgeInSeconds.get();
            if (v < 0) {
                throw new IllegalArgumentException("maxAgeInSeconds must greater or equal to 0");
            }
            maxAge += String.format("max-age=%d", v);
        }

        if (maxStaleInSeconds.isPresent()) {
            long v = maxStaleInSeconds.get();
            if (v < 0) {
                throw new IllegalArgumentException("maxStaleInSeconds must greater or equal to 0");
            }
            if (!maxAge.isEmpty()) {
                maxAge += ",";
            }
            maxAge += String.format("stale-if-error=%d", v);
        }
        Map<String, String> headers;
        if (maxAge.isEmpty()) {
            headers = Map.of();
        } else {
            headers = Map.of("Cache-Control", maxAge);
        }
        return new ConsistencyMode("CACHED", 3, "cached", headers);
    }

    // The next methods are for compatibility with the old enum type

    /**
     * ConsistencyMode used t be an enum, implement it.
     *
     * @return the old enum name
     */
    public final String name() {
        return name;
    }

    @Override
    public final String toString() {
        var builder = new StringBuilder(name());
        for (var entry : getAdditionalHeaders().entrySet()) {
            builder.append(String.format("[%s=%s]", entry.getKey(), entry.getValue()));
        }

        return builder.toString();
    }

    public int ordinal() {
        return ordinal;
    }

    public static ConsistencyMode[] values() {
        var res = new ConsistencyMode[3];
        res[0] = DEFAULT;
        res[1] = STALE;
        res[2] = CONSISTENT;
        // Don't push CACHED as it is just to keep backward compatibility
        return res;
    }
}
