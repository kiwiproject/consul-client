package org.kiwiproject.consul.util.failover.strategy;

import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Defines a contract for selecting a Consul failover server when a request fails.
 * <p>
 * Implementations of this interface are used by
 * {@link org.kiwiproject.consul.util.failover.ConsulFailoverInterceptor ConsulFailoverInterceptor}.
 */
public interface ConsulFailoverStrategy {

    /**
     * Computes the next failover stage for the consul failover strategy. This allows the end user to customize the way
     * and methods by which additional failover targets may be selected.
     *
     * @param previousRequest  The last request to go out the door.
     * @return An optional failover request. This may return an empty optional, signaling that the request should be aborted
     */
    @NonNull
    Optional<Request> computeNextStage(Request previousRequest);

    /**
     * Computes the next failover stage for the consul failover strategy. This allows the end user to customize the way
     * and methods by which additional failover targets may be selected.
     *
     * @param previousRequest  The last request to go out the door.
     * @param previousResponse The response that was returned when the previousRequest was completed.
     * @return An optional failover request. This may return an empty optional, signaling that the request should be aborted
     * @deprecated for removal in 2.0.0, replaced by {@link #computeNextStage(Request)}
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    @SuppressWarnings({ "java:S1133" })
    @NonNull
    Optional<Request> computeNextStage(@NonNull Request previousRequest, @Nullable Response previousResponse);

    /**
     * Determines if there is a viable candidate for the next request. This lets us short circuit the first attempted request
     * (such as when we know with certainty that a host should not be available) without interfering with the consul client too
     * much.
     *
     * @param request The current inflight request.
     * @return A boolean representing if there is another possible request candidate available.
     */
    boolean isRequestViable(@NonNull Request request);

    /**
     * Marks the specified request as a failed URL (in case of exceptions and other events that could cause
     * us to never get a response). This avoids infinite loops where the strategy can never be made aware that the request
     * has failed.
     *
     * @param request The request that failed
     */
    void markRequestFailed(@NonNull Request request);

    /**
     * Reset the state when all options are exhausted (if needed).
     * <p>
     * Use this in implementations that need to keep internal state
     * during a failover attempt. If the implementation is stateless,
     * then there is no need to implement this method.
     * <p>
     * The default implementation does nothing.
     */
    default void reset() {
        // no-op
    }
}
