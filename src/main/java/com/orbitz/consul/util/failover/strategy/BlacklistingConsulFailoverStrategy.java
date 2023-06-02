package com.orbitz.consul.util.failover.strategy;

import static java.util.Objects.nonNull;

import com.google.common.net.HostAndPort;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Troy Heanssgen
 */
public class BlacklistingConsulFailoverStrategy implements ConsulFailoverStrategy {

    // The map of blacklisted addresses
    private final Map<HostAndPort, Instant> blacklist = new ConcurrentHashMap<>();

    // The map of viable targets
    private final Collection<HostAndPort> targets;

    // The blacklist timeout
    private final long timeout;

    /**
     * Constructs a blacklisting strategy with a collection of hosts and ports
     *
     * @param targets A set of viable hosts
     * @param timeout The timeout in milliseconds
     */
    public BlacklistingConsulFailoverStrategy(Collection<HostAndPort> targets, long timeout) {
        this.targets = targets;
        this.timeout = timeout;
    }

    @Override
    public Optional<Request> computeNextStage(Request previousRequest, Response previousResponse) {

        // Create a host and port
        final HostAndPort initialTarget = fromRequest(previousRequest);

        // If the previous response failed, disallow this request from going through.
        // A 404 does NOT indicate a failure in this case, so it should never blacklist the previous target.
        if (previousResponseFailedAndWasNot404(previousResponse)) {
            this.blacklist.put(initialTarget, Instant.now());
        }

        // If our blacklist contains the target we care about
        if (blacklist.containsKey(initialTarget)) {

            // Find the first entity that doesn't exist in the blacklist
            Optional<HostAndPort> optionalNext = findTargetNotInBlacklist();

            if (optionalNext.isEmpty()) {
                return Optional.empty();
            }
            HostAndPort next = optionalNext.get();

            // Construct the next URL using the old parameters (ensures we don't have to do
            // a copy-on-write
            final HttpUrl nextURL = previousRequest.url().newBuilder().host(next.getHost()).port(next.getPort()).build();

            // Return the result
            return Optional.ofNullable(previousRequest.newBuilder().url(nextURL).build());
        } else {

            // Construct the next URL using the old parameters (ensures we don't have to do
            // a copy-on-write
            final HttpUrl nextURL = previousRequest.url().newBuilder().host(initialTarget.getHost()).port(initialTarget.getPort()).build();

            // Return the result
            return Optional.ofNullable(previousRequest.newBuilder().url(nextURL).build());
        }

    }

    /**
     * Find a target that is not blacklisted.
     *
     * @return an Optional containing a target, or an empty Optional of all are blacklisted
     * @implNote This may mutate the blacklist instance field to remove a target from it.
     */
    private Optional<HostAndPort> findTargetNotInBlacklist() {
        return targets.stream().filter(target -> {

            // If we have blacklisted this key
            if (blacklist.containsKey(target)) {

                // Get when we blacklisted this key
                final Instant blacklistedAt = blacklist.get(target);

                // If the duration between the blacklist time ("then") and "now" is greater than the
                // timeout duration (Duration(then, now) - timeout < 0), then remove the blacklist entry
                if (Duration.between(blacklistedAt, Instant.now()).minusMillis(timeout).isNegative()) {
                    return false;
                } else {
                    blacklist.remove(target);
                    return true;
                }
            } else {
                return true;
            }
        }).findAny();
    }

    private static boolean previousResponseFailedAndWasNot404(Response previousResponse) {
        return nonNull(previousResponse) && !previousResponse.isSuccessful() && previousResponse.code() != 404;
    }

    @Override
    public boolean isRequestViable(Request current) {
        return (targets.size() > blacklist.size()) || !blacklist.containsKey(fromRequest(current));
    }

    @Override
    public void markRequestFailed(Request current) {
        this.blacklist.put(fromRequest(current), Instant.now());
    }

    /**
     * Reconstructs a HostAndPort instance from the request object
     *
     * @param request
     * @return
     */
    private HostAndPort fromRequest(Request request) {
        return HostAndPort.fromParts(request.url().host(), request.url().port());
    }

}
