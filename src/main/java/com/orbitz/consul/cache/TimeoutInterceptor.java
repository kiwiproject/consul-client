package com.orbitz.consul.cache;

import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.orbitz.consul.config.CacheConfig;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
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

    @NotNull
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

    @VisibleForTesting
    @Nullable
    static Duration parseWaitQuery(String query) {
        if (Strings.isNullOrEmpty(query)) {
            return null;
        }

        Duration wait = null;
        try {
            if (query.contains("m")) {
                wait = Duration.ofMinutes(Long.parseLong(query.replace("m","")));
            } else if (query.contains("s")) {
                wait = Duration.ofSeconds(Long.parseLong(query.replace("s","")));
            }
        } catch (Exception e) {
            LOG.warn(String.format("Error while extracting wait duration from query parameters: %s", query));
        }
        return wait;
    }
}
