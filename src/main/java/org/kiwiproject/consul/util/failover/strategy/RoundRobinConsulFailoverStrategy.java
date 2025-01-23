package org.kiwiproject.consul.util.failover.strategy;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ThreadUtils.sleepQuietly;
import static org.kiwiproject.consul.util.HostAndPorts.hostAndPortFromOkHttpRequest;
import static org.kiwiproject.consul.util.Lists.isNotNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ConsulFailoverStrategy} that round-robins a list of Consul servers.
 */
public class RoundRobinConsulFailoverStrategy implements ConsulFailoverStrategy {

    // set to invalid index for initial state, so that we always try the first target initially
    @VisibleForTesting
    final ThreadLocal<Integer> lastTargetIndexThreadLocal = ThreadLocal.withInitial(() -> -1);

    private final List<HostAndPort> targets;
    private final int numberOfTargets;
    private final Duration delay;
    private final boolean delayAfterFailedRequest;

    /**
     * Create a new instance with the given list of Consul target servers.
     *
     * @param targets the Consul servers
     */
    public RoundRobinConsulFailoverStrategy(List<HostAndPort> targets) {
        this(targets, Duration.ZERO);
    }

    /**
     * Create a new instance with the given list of Consul target servers.
     * <p>
     * After each failed request, the instance will sleep for the given delay.
     *
     * @param targets           the Consul servers
     * @param delayAfterFailure the amount to sleep after a failed request
     */
    public RoundRobinConsulFailoverStrategy(List<HostAndPort> targets, Duration delayAfterFailure) {
        checkArgument(isNotNullOrEmpty(targets), "targets must not be null or empty");
        this.targets = List.copyOf(targets);
        this.numberOfTargets = targets.size();

        checkArgument(nonNull(delayAfterFailure), "delayAfterFailure must not be null");
        var millis = delayAfterFailure.toMillis();
        checkArgument(millis >= 0, "delayAfterFailure must be zero or a positive duration");
        this.delay = delayAfterFailure;
        this.delayAfterFailedRequest = millis > 0;
    }

    @Override
    @NonNull
    public Optional<Request> computeNextStage(Request previousRequest) {
        return computeNextStage(previousRequest, null);
    }

    @SuppressWarnings("removal")
    @Override
    @NonNull
    public Optional<Request> computeNextStage(@NonNull Request previousRequest, @Nullable Response previousResponse) {
        var nextIndex = lastTargetIndexThreadLocal.get() + 1;

        if (nextIndex >= numberOfTargets) {
            return Optional.empty();
        }

        if (nextIndex > 0) {
            sleepIfPositiveDelay();
        }

        var nextTarget = targets.get(nextIndex);
        HttpUrl nextURL = previousRequest.url().newBuilder()
                .host(nextTarget.getHost())
                .port(nextTarget.getPort())
                .build();
        return Optional.of(previousRequest.newBuilder().url(nextURL).build());
    }

    private void sleepIfPositiveDelay() {
        if (delayAfterFailedRequest) {
            sleepQuietly(delay);
        }
    }

    @Override
    public boolean isRequestViable(@NonNull Request request) {
        // always viable since we will move to the next target after a failure
        return true;
    }

    @Override
    public void markRequestFailed(@NonNull Request request) {
        var hostAndPort = hostAndPortFromOkHttpRequest(request);
        lastTargetIndexThreadLocal.set(targets.indexOf(hostAndPort));
    }

    @Override
    public void reset() {
        lastTargetIndexThreadLocal.remove();
    }
}
