package org.kiwiproject.consul.option;

import static java.util.Objects.nonNull;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Represents the desired consistency level for Consul queries.
 * <p>
 * This class was originally an enum but has been refactored into a class to allow additional, dynamic
 * consistency modes such as cached modes with configurable cache-control headers. It retains methods
 * ({@link #name()}, {@link #ordinal()}, and {@link #values()}) for backward compatibility with the
 * former enum type.
 * <p>
 * The predefined modes are:
 * <ul>
 *   <li>{@link #DEFAULT} – Uses the default Consul consistency behavior (read from leader or follower).</li>
 *   <li>{@link #STALE} – Allows reading potentially stale data from any Consul server.</li>
 *   <li>{@link #CONSISTENT} – Forces reads from the leader to ensure the most up-to-date data.</li>
 * </ul>
 * <p>
 * In addition, factory methods such as {@link #createCachedConsistencyWithMaxAgeAndStale(Long, Long)}
 * can create cached consistency modes with custom ache-control headers.
 * <p>
 * Instances are immutable and thread-safe.
 */
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
     * Get the Additional HTTP headers to add to the request.
     *
     * @return a not null but possibly empty map
     */
    public final Map<String, String> getAdditionalHeaders() {
        return additionalHeaders;
    }

    /**
     * Creates a cached Consistency.
     *
     * @param maxAgeInSeconds   Optional duration in seconds after which data is considered too old
     * @param maxStaleInSeconds Optional duration in seconds for which data can be stale if the server cannot be reached
     * @return a not null ConsistencyMode
     * @see <a href="https://developer.hashicorp.com/consul/api-docs/features/caching#simple-caching">Simple Caching</a>
     */
    public static ConsistencyMode createCachedConsistencyWithMaxAgeAndStale(Optional<Long> maxAgeInSeconds,
                                                                            Optional<Long> maxStaleInSeconds) {
        return createCachedConsistencyWithMaxAgeAndStale(
                maxAgeInSeconds.orElse(null),
                maxStaleInSeconds.orElse(null)
        );
    }

    /**
     * Creates a cached Consistency.
     *
     * @param maxAgeInSeconds   duration in seconds after which data is considered too old
     * @param maxStaleInSeconds duration in seconds for which data can be stale if the server cannot be reached
     * @return a new {@code ConsistencyMode} instance
     * @see <a href="https://developer.hashicorp.com/consul/api-docs/features/caching#simple-caching">Simple Caching</a>
     */
    public static ConsistencyMode createCachedConsistencyWithMaxAgeAndStale(@Nullable Long maxAgeInSeconds,
                                                                            @Nullable Long maxStaleInSeconds) {
        var maxAge = "";
        if (nonNull(maxAgeInSeconds)) {
            if (maxAgeInSeconds < 0) {
                throw new IllegalArgumentException("maxAgeInSeconds must be greater than or equal to 0");
            }
            maxAge += String.format("max-age=%d", maxAgeInSeconds);
        }

        if (nonNull(maxStaleInSeconds)) {
            if (maxStaleInSeconds < 0) {
                throw new IllegalArgumentException("maxStaleInSeconds must be greater than or equal to 0");
            }
            if (!maxAge.isEmpty()) {
                maxAge += ",";
            }
            maxAge += String.format("stale-if-error=%d", maxStaleInSeconds);
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
     * Backward-compatibility with the former enum: returns the enum-style constant name.
     * <p>
     * This class used to be an enum; the {@code name()}, {@link #ordinal()}, and {@link #values()}
     * methods are retained to preserve source/binary compatibility.
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

    /**
     * Backward-compatibility with the former enum: returns the enum-style ordinal.
     * <p>
     * Predefined modes use ordinals 0..2. Factory-produced cached modes use ordinal 3 and are not
     * returned by {@link #values()}.
     *
     * @return the ordinal value
     */
    public int ordinal() {
        return ordinal;
    }

    /**
     * Backward-compatibility with the former enum: returns the array of predefined modes.
     * <p>
     * Note: factory-produced cached modes (ordinal 3) are not included. Currently, this
     * is only the "CACHED" consistency mode.
     *
     * @return the predefined consistency modes
     * @see #createCachedConsistencyWithMaxAgeAndStale(Long, Long)
     */
    public static ConsistencyMode[] values() {
        var res = new ConsistencyMode[3];
        res[0] = DEFAULT;
        res[1] = STALE;
        res[2] = CONSISTENT;
        // Don't push CACHED as it is just to keep backward compatibility
        return res;
    }
}
