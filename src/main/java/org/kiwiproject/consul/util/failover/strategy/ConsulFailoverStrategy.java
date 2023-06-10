package org.kiwiproject.consul.util.failover.strategy;

import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;

public interface ConsulFailoverStrategy {


    /**
     * Computes the next failover stage for the consul failover strategy. This allows the end user to customize the way
     * and methods by which additional failover targets may be selected.
     *
     * @param previousRequest  The last request to go out the door.
     * @param previousResponse The response that returned when previousRequest was completed.
     * @return An optional failover request. This may return an empty optional, signaling that the request should be aborted
     */
    @NonNull
    Optional<Request> computeNextStage(@NonNull Request previousRequest, @Nullable Response previousResponse);

    /**
     * Determines if there is a viable candidate for the next request. This lets us short circuit the first attempted request
     * (such as when we know with certainty that a host should not be available) without interfering with the consul client too
     * much.
     *
     * @param current The current inflight request.
     * @return A boolean representing if there is another possible request candidate available.
     */
    boolean isRequestViable(@NonNull Request current);

    /**
     * Marks the specified request as a failed URL (in case of exceptions and other events that could cause
     * us to never get a response). This avoids infinite loops where the strategy can never be made aware that the request
     * has failed.
     *
     * @param current The current request object representing a request that failed
     */
    void markRequestFailed(@NonNull Request current);

}
