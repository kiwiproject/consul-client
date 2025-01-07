package org.kiwiproject.consul.util.failover.strategy;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.consul.util.HostAndPorts.hostAndPortFromOkHttpRequest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
    private final int numberOfTargets;

    // The blacklist timeout
    private final long timeout;

    /**
     * Constructs a blacklisting strategy with a collection of hosts and ports.
     * <p>
     * The timeout is the number of milliseconds that must pass before a blacklisted target
     * host can be removed from the blacklist.
     *
     * @param targets A set of viable hosts
     * @param timeout The timeout in milliseconds
     */
    public BlacklistingConsulFailoverStrategy(Collection<HostAndPort> targets, long timeout) {
        this.targets = List.copyOf(targets);
        this.numberOfTargets = this.targets.size();
        checkArgument(timeout > 0, "timeout must be a positive number of milliseconds");
        this.timeout = timeout;
    }

    @NonNull
    @Override
    public Optional<Request> computeNextStage(Request previousRequest) {
        return computeNextStage(previousRequest, null);
    }

    @SuppressWarnings("removal")
    @NonNull
    @Override
    public Optional<Request> computeNextStage(@NonNull Request previousRequest, @Nullable Response previousResponse) {

        // Create a host and port
        var nextTarget = hostAndPortFromOkHttpRequest(previousRequest);

        // If the previous response failed, disallow this request from going through.
        // A 404 does NOT indicate a failure in this case, so it should never blacklist the previous target.
        if (previousResponseFailedAndWasNot404(previousResponse)) {
            addToBlacklist(nextTarget);
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

        // Exit early if the target is not in the blacklist.
        // The only way this can be true is if a concurrent request causes the
        // target to be removed from the blacklist between the time it is first
        // checked in #findTargetNotInBlacklist and when this method gets the
        // value of the target key above. If that happens, return true. It
        // won't cause any problems with the Map#remove operation since that
        // will just return null if there was no mapping with the given key.
        if (isNull(blacklistedAt)) {
            return true;
        }

        // If "now" is after or equal to the time target was blacklisted plus the timeout amount,
        // then the timeout has passed
        var timeoutExpiration = blacklistedAt.plusMillis(timeout);
        var now = Instant.now();
        return now.equals(timeoutExpiration) || now.isAfter(timeoutExpiration);
    }

    private static boolean previousResponseFailedAndWasNot404(Response previousResponse) {
        return nonNull(previousResponse) && !previousResponse.isSuccessful() && previousResponse.code() != 404;
    }

    @Override
    public boolean isRequestViable(@NonNull Request request) {
        return atLeastOneTargetIsAvailable() ||
                isNotBlacklisted(hostAndPortFromOkHttpRequest(request)) ||
                findTargetNotInBlacklist().isPresent();
    }

    private boolean atLeastOneTargetIsAvailable() {
        return numberOfTargets > blacklist.size();
    }

    private boolean isNotBlacklisted(HostAndPort target) {
        return !isBlacklisted(target);
    }

    private boolean isBlacklisted(HostAndPort target) {
        return blacklist.containsKey(target);
    }

    @Override
    public void markRequestFailed(@NonNull Request request) {
        var hostAndPort = hostAndPortFromOkHttpRequest(request);
        addToBlacklist(hostAndPort);
    }

    @VisibleForTesting
    void addToBlacklist(HostAndPort target) {
        blacklist.put(target, Instant.now());
    }
}
