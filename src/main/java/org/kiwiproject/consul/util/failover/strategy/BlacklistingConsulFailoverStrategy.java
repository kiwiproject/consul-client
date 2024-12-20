package org.kiwiproject.consul.util.failover.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

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
    @VisibleForTesting
    final Map<HostAndPort, Instant> blacklist = new ConcurrentHashMap<>();

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

    @NonNull
    @Override
    public Optional<Request> computeNextStage(@NonNull Request previousRequest, @Nullable Response previousResponse) {

        // Create a host and port
        var nextTarget = hostAndPortFromRequest(previousRequest);

        // If the previous response failed, disallow this request from going through.
        // A 404 does NOT indicate a failure in this case, so it should never blacklist the previous target.
        if (previousResponseFailedAndWasNot404(previousResponse)) {
            addToBlackist(nextTarget);
        }

        // Handle the case when the blacklist contains the target we care about
        if (isBlacklisted(nextTarget)) {

            // Find the first entity that doesn't exist in the blacklist
            Optional<HostAndPort> optionalNext = findTargetNotInBlacklist();

            if (optionalNext.isEmpty()) {
                return Optional.empty();
            }

            nextTarget = optionalNext.get();
        }

        HttpUrl nextURL = previousRequest.url().newBuilder().host(nextTarget.getHost()).port(nextTarget.getPort()).build();
        return Optional.of(previousRequest.newBuilder().url(nextURL).build());
    }

    /**
     * Find a target that is not blacklisted.
     *
     * @return an Optional containing a target, or an empty Optional of all are blacklisted
     * @implNote This may mutate the blacklist instance field to remove a target from it.
     */
    private Optional<HostAndPort> findTargetNotInBlacklist() {
        return targets.stream().filter(target -> {
            if (isNotBlacklisted(target)) {
                return true;
            }

            if (isPastBlacklistDuration(target)) {
                // Remove the target from blacklist once timeout has expired
                blacklist.remove(target);
                return true;
            }

            return false;
        }).findAny();
    }

    @VisibleForTesting
    boolean isPastBlacklistDuration(HostAndPort target) {
        var blacklistedAt = blacklist.get(target);

        // Exit early if the target is not actually in the blacklist.
        // The only way this can be true is if a concurrent request causes the
        // target to be removed from the blacklist between the time it is first
        // checked in #findTargetNotInBlacklist and when this method gets the
        // value of the target key above. If that happens, just return true. It
        // won't cause any problems with the Map#remove operation since that
        // will just return null if there was no mapping with the given key.
        if (isNull(blacklistedAt)) {
            return true;
        }

        // If the duration between the blacklist time ("then") and "now" is greater than the
        // timeout duration (Duration(then, now) - timeout >= 0), then the timeout has passed
        var adjustedBlacklistDuration = Duration.between(blacklistedAt, Instant.now()).minusMillis(timeout);

        return isPositiveOrZero(adjustedBlacklistDuration);
    }

    private static boolean isPositiveOrZero(Duration duration) {
        return !duration.isNegative();
    }

    private static boolean previousResponseFailedAndWasNot404(Response previousResponse) {
        return nonNull(previousResponse) && !previousResponse.isSuccessful() && previousResponse.code() != 404;
    }

    @Override
    public boolean isRequestViable(@NonNull Request current) {
        return findTargetNotInBlacklist().isPresent();
    }

    private boolean isNotBlacklisted(HostAndPort target) {
        return !isBlacklisted(target);
    }

    private boolean isBlacklisted(HostAndPort target) {
        return blacklist.containsKey(target);
    }

    @Override
    public void markRequestFailed(@NonNull Request current) {
        var hostAndPort = hostAndPortFromRequest(current);
        addToBlackist(hostAndPort);
    }

    private HostAndPort hostAndPortFromRequest(Request request) {
        return HostAndPort.fromParts(request.url().host(), request.url().port());
    }

    @VisibleForTesting
    void addToBlackist(HostAndPort target) {
        blacklist.put(target, Instant.now());
    }
}
