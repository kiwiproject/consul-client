package org.kiwiproject.consul.cache;

import com.google.common.primitives.Ints;
import org.kiwiproject.consul.HealthClient;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.model.health.HealthCheck;
import org.kiwiproject.consul.option.QueryOptions;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

public class HealthCheckCache extends ConsulCache<String, HealthCheck> {

    private HealthCheckCache(HealthClient healthClient,
                             org.kiwiproject.consul.model.State checkState,
                             int watchSeconds,
                             QueryOptions queryOptions,
                             Function<HealthCheck, String> keyExtractor,
                             Scheduler callbackScheduler) {
        super(keyExtractor,
            (index, callback) -> {
                checkWatch(healthClient.getNetworkTimeoutConfig().getClientReadTimeoutMillis(), watchSeconds);
                QueryOptions params = watchParams(index, watchSeconds, queryOptions);
                healthClient.getChecksByState(checkState, params, callback);
            },
            healthClient.getConfig().getCacheConfig(),
            healthClient.getEventHandler(),
            new CacheDescriptor("health.state", checkState.getName()),
            callbackScheduler);
    }

    /**
     * Factory method to construct a string/{@link HealthCheck} map for a particular {@link org.kiwiproject.consul.model.State}.
     * <p>
     * Keys will be the {@link HealthCheck#getCheckId()}.
     *
     * @param healthClient            the {@link HealthClient}
     * @param checkState              the state fo filter checks
     * @param watchSeconds            the seconds to block
     * @param queryOptions            the query options to use
     * @param keyExtractor            a function to convert values to keys
     * @param callbackExecutorService the ScheduledExecutorService to use for asynchronous callbacks
     * @return a cache object
     */
    public static HealthCheckCache newCache(
            final HealthClient healthClient,
            final org.kiwiproject.consul.model.State checkState,
            final int watchSeconds,
            final QueryOptions queryOptions,
            final Function<HealthCheck, String> keyExtractor,
            final ScheduledExecutorService callbackExecutorService) {

        Scheduler callbackScheduler = createExternal(callbackExecutorService);
        return new HealthCheckCache(healthClient, checkState, watchSeconds, queryOptions, keyExtractor, callbackScheduler);
    }

    public static HealthCheckCache newCache(
            final HealthClient healthClient,
            final org.kiwiproject.consul.model.State checkState,
            final int watchSeconds,
            final QueryOptions queryOptions,
            final Function<HealthCheck, String> keyExtractor) {

        return new HealthCheckCache(healthClient, checkState, watchSeconds, queryOptions, keyExtractor, createDefault());
    }
    public static HealthCheckCache newCache(
            final HealthClient healthClient,
            final org.kiwiproject.consul.model.State checkState,
            final int watchSeconds,
            final QueryOptions queryOptions) {

        return newCache(healthClient, checkState, watchSeconds, queryOptions, HealthCheck::getCheckId);
    }

    public static HealthCheckCache newCache(
            final HealthClient healthClient,
            final org.kiwiproject.consul.model.State checkState,
            final int watchSeconds) {

        return newCache(healthClient, checkState, watchSeconds, QueryOptions.BLANK);
    }

    public static HealthCheckCache newCache(final HealthClient healthClient, final org.kiwiproject.consul.model.State checkState) {
        CacheConfig cacheConfig = healthClient.getConfig().getCacheConfig();
        int watchSeconds = Ints.checkedCast(cacheConfig.getWatchDuration().getSeconds());
        return newCache(healthClient, checkState, watchSeconds);
    }

}
