package org.kiwiproject.consul.util.failover;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.net.HostAndPort;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.util.failover.strategy.BlacklistingConsulFailoverStrategy;
import org.kiwiproject.consul.util.failover.strategy.ConsulFailoverStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

public class ConsulFailoverInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulFailoverInterceptor.class);

    /**
     * The default maximum number of failover attempts.
     */
    private static final int DEFAULT_MAX_FAILOVER_ATTEMPTS = 10;

    // The consul failover strategy
    private final ConsulFailoverStrategy strategy;

    // The maximum number of failover attempts before giving up and throwing an exception
    private int maxFailoverAttempts = DEFAULT_MAX_FAILOVER_ATTEMPTS;

    /**
     * Default constructor for a set of hosts and ports
     *
     * @param targets the host/port pairs to use for failover
     * @param timeout the timeout in milliseconds
     */
    public ConsulFailoverInterceptor(Collection<HostAndPort> targets, long timeout) {
        this(new BlacklistingConsulFailoverStrategy(targets, timeout));
    }

    /**
     * Allows customization of the interceptor chain
     *
     * @param strategy the failover strategy
     */
    public ConsulFailoverInterceptor(ConsulFailoverStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Change the maximum number of failover attempts before giving up.
     * The default value is 10.
     * <p>
     * While this method can be called at any time after construction,
     * it is intended to be called immediately following a constructor
     * when there is a need to change the default maximum. For example:
     * <pre>
     * var interceptor = new ConsulFailoverInterceptor(hosts, timeout)
     *         .withMaxFailoverAttempts(25);
     * </pre>
     *
     * @param maxFailoverAttempts the new maximum number of failover attempts
     * @return this instance, to permit chaining on a constructor call
     */
    public ConsulFailoverInterceptor withMaxFailoverAttempts(int maxFailoverAttempts) {
        checkArgument(maxFailoverAttempts > 0, "maxFailoverAttempts must be positive");
        this.maxFailoverAttempts = maxFailoverAttempts;
        return this;
    }

    /**
     * Return the maximum number of failover attempts, after which a
     * {@link MaxFailoverAttemptsExceededException} is thrown.
     *
     * @return maximum failover attempts
     */
    public int maxFailoverAttempts() {
        return maxFailoverAttempts;
    }

    @NotNull
    @Override
    public Response intercept(Chain chain) {

        // The original request
        Request originalRequest = chain.request();

        // If it is possible to do a failover on the first request (as in, one or more
        // targets are viable)
        if (strategy.isRequestViable(originalRequest)) {

            // Initially, we have an inflight request
            Request previousRequest = originalRequest;

            Optional<Request> nextRequest;

            // Note:
            // The previousResponse is never used here and is always null when calling computeNextStage.
            // See discussion in issue #195 ("previousResponse is always null in ConsulFailoverInterceptor#intercept")
            // Link: https://github.com/kiwiproject/consul-client/issues/195

            var currentAttempt = 1;

            // Get the next viable request
            while ((nextRequest = strategy.computeNextStage(previousRequest, null)).isPresent()) {
                // Get the response from the last viable request
                Exception exception;
                try {

                    // Cache for the next cycle if needed
                    final Request next = nextRequest.get();
                    previousRequest = next;

                    // Anything other than an exception is valid here.
                    // This is because a 400-series error is a valid code (Permission Denied/Key Not Found)
                    return chain.proceed(next);
                } catch (Exception ex) {
                    LOG.debug("Failed to connect to {}", nextRequest.get().url(), ex);
                    strategy.markRequestFailed(nextRequest.get());
                    exception = ex;
                }

                if (++currentAttempt > maxFailoverAttempts) {
                    throw new MaxFailoverAttemptsExceededException(
                            String.format("Reached max failover attempts (%d). Giving up.", maxFailoverAttempts),
                            exception);
                }
            }

            throw new ConsulException("Unable to successfully determine a viable host for communication.");

        } else {
            throw new ConsulException(
                    "Consul failover strategy has determined that there are no viable hosts remaining.");
        }
    }
}
