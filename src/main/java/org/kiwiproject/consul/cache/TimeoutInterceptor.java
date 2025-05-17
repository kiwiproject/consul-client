package org.kiwiproject.consul.cache;

import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.consul.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TimeoutInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(TimeoutInterceptor.class);

    private final CacheConfig config;

    public TimeoutInterceptor(CacheConfig config) {
        this.config = config;
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        int readTimeout = chain.readTimeoutMillis();

        // Snapshot might be very large. Timeout should be adjusted for this endpoint.
        if (request.url().encodedPath().contains("snapshot")) {
            readTimeout = (int) Duration.ofHours(1).toMillis();
        } else if (config.isTimeoutAutoAdjustmentEnabled()) {
            String waitQuery = request.url().queryParameter("wait");
            Duration waitDuration = parseWaitQuery(waitQuery);
            if (nonNull(waitDuration)) {
                int waitDurationMs = (int) waitDuration.toMillis();
                int readTimeoutConfigMargin = (int) config.getTimeoutAutoAdjustmentMargin().toMillis();

                // According to https://developer.hashicorp.com/consul/api-docs/features/blocking
                // A small random amount of additional wait time is added to the supplied maximum wait time by consul
                // agent to spread out the wake-up time of any concurrent requests.
                // This adds up to (wait / 16) additional time to the maximum duration.
                int readTimeoutRequiredMargin = (int) Math.ceil((double)(waitDurationMs) / 16);

                readTimeout = waitDurationMs + readTimeoutRequiredMargin + readTimeoutConfigMargin;
            }
        }

        return chain
                .withReadTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .proceed(request);
    }

    /**
     * Parse the query for a Blocking Query. It will be used in tbe value of the {@code wait}
     * query parameter.
     * <p>
     * For details on the {@code wait} query parameter, see
     * <a href="https://developer.hashicorp.com/consul/api-docs/features/blocking#blocking-queries">Blocking Queries</a>.
     *
     * @param query the value for the {@code wait} query parameter
     * @return the Duration equivalent to the wait query, or null if the query is null, empty, or is not valid
     */
    @VisibleForTesting
    @Nullable
    static Duration parseWaitQuery(String query) {
        if (Strings.isNullOrEmpty(query)) {
            return null;
        }

        try {
            if (query.endsWith("m")) {
                return Duration.ofMinutes(numberFrom(query));
            } else if (query.endsWith("s")) {
                return Duration.ofSeconds(numberFrom(query));
            }
        } catch (Exception ignored) {
            // intentionally ignored
        }

        LOG.warn("Invalid wait query '{}'. Expected a number plus 's' or 'm' ('10s' for seconds, '5m' for minutes).", query);
        return null;
    }

    private static long numberFrom(String query) {
        return Long.parseLong(query.substring(0, query.length() - 1));
    }
}
