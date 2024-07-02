package org.kiwiproject.consul.cache;

import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import org.kiwiproject.consul.HealthClient;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.model.health.ServiceHealth;
import org.kiwiproject.consul.option.Options;
import org.kiwiproject.consul.option.QueryOptions;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

public class ServiceHealthCache extends ConsulCache<ServiceHealthKey, ServiceHealth> {

    private ServiceHealthCache(HealthClient healthClient,
                               String serviceName,
                               boolean passing,
                               int watchSeconds,
                               QueryOptions queryOptions,
                               Function<ServiceHealth, ServiceHealthKey> keyExtractor,
                               Scheduler callbackScheduler) {
        super(keyExtractor,
              (index, callback) -> {
                  checkWatch(healthClient.getNetworkTimeoutConfig().getClientReadTimeoutMillis(), watchSeconds);
                  QueryOptions params = watchParams(index, watchSeconds, queryOptions);
                  if (passing) {
                      healthClient.getHealthyServiceInstances(serviceName, params, callback);
                  } else {
                      healthClient.getAllServiceInstances(serviceName, params, callback);
                  }
              },
              healthClient.getConfig().getCacheConfig(),
              healthClient.getEventHandler(),
              new CacheDescriptor("health.service", serviceName),
              callbackScheduler);
    }

    /**
     * Factory method to construct a string/{@link ServiceHealth} map for a particular service.
     * <p>
     * Keys will be a {@link HostAndPort} object made up of the service's address/port combo
     *
     * @param healthClient            the {@link HealthClient}
     * @param serviceName             the name of the service
     * @param passing                 include only passing services?
     * @param watchSeconds            the seconds to block
     * @param queryOptions            the query options to use
     * @param keyExtractor            a function to convert values to keys
     * @param callbackExecutorService the ScheduledExecutorService to use for asynchronous callbacks
     * @return a cache object
     */
    public static ServiceHealthCache newCache(
            final HealthClient healthClient,
            final String serviceName,
            final boolean passing,
            final int watchSeconds,
            final QueryOptions queryOptions,
            final Function<ServiceHealth, ServiceHealthKey> keyExtractor,
            final ScheduledExecutorService callbackExecutorService) {

        Scheduler scheduler = createExternal(callbackExecutorService);
        return new ServiceHealthCache(healthClient, serviceName, passing, watchSeconds, queryOptions, keyExtractor, scheduler);
    }

    public static ServiceHealthCache newCache(
            final HealthClient healthClient,
            final String serviceName,
            final boolean passing,
            final int watchSeconds,
            final QueryOptions queryOptions,
            final Function<ServiceHealth, ServiceHealthKey> keyExtractor) {

        return new ServiceHealthCache(healthClient, serviceName, passing, watchSeconds, queryOptions, keyExtractor, createDefault());
    }

    public static ServiceHealthCache newCache(
            final HealthClient healthClient,
            final String serviceName,
            final boolean passing,
            final int watchSeconds,
            final QueryOptions queryOptions) {

        return newCache(healthClient, serviceName, passing, watchSeconds, queryOptions, ServiceHealthKey::fromServiceHealth);
    }

    public static ServiceHealthCache newCache(
            final HealthClient healthClient,
            final String serviceName,
            final boolean passing,
            final QueryOptions queryOptions,
            final int watchSeconds) {

        return newCache(healthClient, serviceName, passing, watchSeconds, queryOptions);
    }

    public static ServiceHealthCache newCache(final HealthClient healthClient, final String serviceName) {
        CacheConfig cacheConfig = healthClient.getConfig().getCacheConfig();
        int watchSeconds = Ints.checkedCast(cacheConfig.getWatchDuration().getSeconds());
        return newCache(healthClient, serviceName, true, Options.BLANK_QUERY_OPTIONS, watchSeconds);
    }
}
