package com.orbitz.consul.config;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;

import java.time.Duration;

public class CacheConfig {

    @VisibleForTesting
    static final Duration DEFAULT_WATCH_DURATION = Duration.ofSeconds(10);
    @VisibleForTesting
    static final Duration DEFAULT_BACKOFF_DELAY = Duration.ofSeconds(10);
    @VisibleForTesting
    static final Duration DEFAULT_MIN_DELAY_BETWEEN_REQUESTS = Duration.ZERO;
    @VisibleForTesting
    static final Duration DEFAULT_MIN_DELAY_ON_EMPTY_RESULT = Duration.ZERO;
    @VisibleForTesting
    static final boolean DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED = true;
    @VisibleForTesting
    static final Duration DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN = Duration.ofSeconds(2);
    @VisibleForTesting
    static final RefreshErrorLogConsumer DEFAULT_REFRESH_ERROR_LOG_CONSUMER = Logger::error;

    private final Duration watchDuration;
    private final Duration minBackOffDelay;
    private final Duration maxBackOffDelay;
    private final Duration minDelayBetweenRequests;
    private final Duration minDelayOnEmptyResult;
    private final Duration timeoutAutoAdjustmentMargin;
    private final boolean timeoutAutoAdjustmentEnabled;
    private final RefreshErrorLogConsumer refreshErrorLogConsumer;

    private CacheConfig(Duration watchDuration,
                        Duration minBackOffDelay,
                        Duration maxBackOffDelay,
                        Duration minDelayBetweenRequests,
                        Duration minDelayOnEmptyResult,
                        boolean timeoutAutoAdjustmentEnabled,
                        Duration timeoutAutoAdjustmentMargin,
                        RefreshErrorLogConsumer refreshErrorLogConsumer) {
        this.watchDuration = watchDuration;
        this.minBackOffDelay = minBackOffDelay;
        this.maxBackOffDelay = maxBackOffDelay;
        this.minDelayBetweenRequests = minDelayBetweenRequests;
        this.minDelayOnEmptyResult = minDelayOnEmptyResult;
        this.timeoutAutoAdjustmentEnabled = timeoutAutoAdjustmentEnabled;
        this.timeoutAutoAdjustmentMargin = timeoutAutoAdjustmentMargin;
        this.refreshErrorLogConsumer = refreshErrorLogConsumer;
    }

    /**
     * Gets the default watch duration for caches.
     *
     * @return the watch duration
     */
    public Duration getWatchDuration() {
        return watchDuration;
    }

    /**
     * Gets the minimum back-off delay used in caches.
     *
     * @return the minimum back-off delay
     */
    public Duration getMinimumBackOffDelay() {
        return minBackOffDelay;
    }

    /**
     * Gets the maximum back-off delay used in caches.
     *
     * @return maximum back-off delay
     */
    public Duration getMaximumBackOffDelay() {
        return maxBackOffDelay;
    }

    /**
     * Is the automatic adjustment of read timeout enabled?
     *
     * @return true if automatic adjustment of read timeout is enabled, otherwise false
     */
    public boolean isTimeoutAutoAdjustmentEnabled() {
       return timeoutAutoAdjustmentEnabled;
    }

    /**
     * Gets the margin of the read timeout for caches.
     * <p>
     * The margin represents the additional amount of time given to the read timeout, in addition to the wait duration.
     *
     * @return the margin of the read timeout
     */
    public Duration getTimeoutAutoAdjustmentMargin() {
        return timeoutAutoAdjustmentMargin;
    }

    /**
     * Gets the minimum time between two requests for caches.
     *
     * @return the minimum time between two requests
     */
    public Duration getMinimumDurationBetweenRequests() {
        return minDelayBetweenRequests;
    }

    /**
     * Gets the minimum time between two requests for caches.
     *
     * @return the minimum time between two requests
     */
    public Duration getMinimumDurationDelayOnEmptyResult() {
        return minDelayOnEmptyResult;
    }

    /**
     * Gets the function that will be called in case of error.
     *
     * @return a RefreshErrorLogConsumer that will be called when errors occur
     */
    public RefreshErrorLogConsumer getRefreshErrorLoggingConsumer() {
        return refreshErrorLogConsumer;
    }

    /**
     * Creates a new {@link CacheConfig.Builder} object.
     *
     * @return A new Consul builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private static final String DELAY_CANNOT_BE_NULL = "Delay cannot be null";

        private Duration watchDuration = DEFAULT_WATCH_DURATION;
        private Duration minBackOffDelay = DEFAULT_BACKOFF_DELAY;
        private Duration maxBackOffDelay = DEFAULT_BACKOFF_DELAY;
        private Duration minDelayBetweenRequests = DEFAULT_MIN_DELAY_BETWEEN_REQUESTS;
        private Duration minDelayOnEmptyResult = DEFAULT_MIN_DELAY_ON_EMPTY_RESULT;
        private Duration timeoutAutoAdjustmentMargin = DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN;
        private boolean timeoutAutoAdjustmentEnabled = DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED;
        private RefreshErrorLogConsumer refreshErrorLogConsumer = DEFAULT_REFRESH_ERROR_LOG_CONSUMER;

        private Builder() {

        }

        /**
         * Sets the watch duration used in caches.
         *
         * @param delay the watch duration to use
         * @return the Builder instance
         * @throws IllegalArgumentException if {@code delay} is negative.
         */
        public Builder withWatchDuration(Duration delay) {
            this.watchDuration = checkNotNull(delay, DELAY_CANNOT_BE_NULL);
            checkArgument(!delay.isNegative(), "Delay must be positive");
            return this;
        }

        /**
         * Sets the back-off delay used in caches.
         *
         * @param delay the back-off delay to use
         * @return the Builder instance
         * @throws IllegalArgumentException if {@code delay} is negative.
         */
        public Builder withBackOffDelay(Duration delay) {
            this.minBackOffDelay = checkNotNull(delay, DELAY_CANNOT_BE_NULL);
            this.maxBackOffDelay = delay;
            checkArgument(!delay.isNegative(), "Delay must be positive");
            return this;
        }

        /**
         * Sets a random delay between the {@code minDelay} and {@code maxDelay} (inclusive) to occur between retries.
         *
         * @param minDelay the minimum delay between retries
         * @param maxDelay the maximum delay between retries
         * @return the Builder instance
         * @throws IllegalArgumentException if {@code minDelay} or {@code maxDelay} is negative, or if {@code minDelay}
         *                                  is greater than to {@code maxDelay}.
         */
        public Builder withBackOffDelay(Duration minDelay, Duration maxDelay) {
            this.minBackOffDelay = checkNotNull(minDelay, "Minimum delay cannot be null");
            this.maxBackOffDelay = checkNotNull(maxDelay, "Maximum delay cannot be null");
            checkArgument(!minDelay.isNegative(), "Minimum delay must be positive");
            checkArgument(!maxDelay.minus(minDelay).isNegative(), "Minimum delay must be less than maximum delay");
            return this;
        }

        /**
         * Sets the minimum time between two requests for caches.
         *
         * @param delay the minimum time between two requests to use
         * @return the Builder instance
         */
        public Builder withMinDelayBetweenRequests(Duration delay) {
            this.minDelayBetweenRequests = checkNotNull(delay, DELAY_CANNOT_BE_NULL);
            return this;
        }

        /**
         * Sets the minimum time between two requests for caches when an empty result is returned.
         *
         * @param delay the minimum time between two requests to use when an empty result is returned
         * @return the Builder instance
         */
        public Builder withMinDelayOnEmptyResult(Duration delay) {
            this.minDelayOnEmptyResult = checkNotNull(delay, DELAY_CANNOT_BE_NULL);
            return this;
        }

        /**
         * Enable/Disable the automatic adjustment of read timeout
         *
         * @param enabled use true to enable automatic adjustment of read timeout, false to disable
         * @return the Builder instance
         */
        public Builder withTimeoutAutoAdjustmentEnabled(boolean enabled) {
            this.timeoutAutoAdjustmentEnabled = enabled;
            return this;
        }

        /**
         * Sets the margin of the read timeout for caches.
         * <p>
         * The margin represents the additional amount of time given to the read timeout, in addition to the wait duration.
         *
         * @param margin the margin of the read timeout to use
         * @return the Builder instance
         */
        public Builder withTimeoutAutoAdjustmentMargin(Duration margin) {
            this.timeoutAutoAdjustmentMargin = checkNotNull(margin, "Margin cannot be null");
            return this;
        }

        /**
         * Log refresh errors as warning
         *
         * @return the Builder instance
         */
        public Builder withRefreshErrorLoggedAsWarning() {
            this.refreshErrorLogConsumer = Logger::warn;
            return this;
        }

        /**
         * Log refresh errors as error
         *
         * @return the Builder instance
         */
        public Builder withRefreshErrorLoggedAsError() {
            this.refreshErrorLogConsumer = Logger::error;
            return this;
        }

        /**
         * Log refresh errors using custom function
         *
         * @param fn the custom function to use when an error occurs
         * @return the Builder instance
         */
        public Builder withRefreshErrorLoggedAs(RefreshErrorLogConsumer fn) {
            this.refreshErrorLogConsumer = fn;
            return this;
        }

        public CacheConfig build() {
            return new CacheConfig(watchDuration,
                    minBackOffDelay,
                    maxBackOffDelay,
                    minDelayBetweenRequests,
                    minDelayOnEmptyResult,
                    timeoutAutoAdjustmentEnabled,
                    timeoutAutoAdjustmentMargin,
                    refreshErrorLogConsumer);
        }
    }

    public interface RefreshErrorLogConsumer {
        void accept(Logger logger, String message, Throwable error);
    }
}
