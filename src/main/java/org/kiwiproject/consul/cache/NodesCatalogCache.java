package org.kiwiproject.consul.cache;

import com.google.common.primitives.Ints;
import org.kiwiproject.consul.CatalogClient;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.model.health.Node;
import org.kiwiproject.consul.option.QueryOptions;

import java.util.concurrent.ScheduledExecutorService;

public class NodesCatalogCache extends ConsulCache<String, Node> {

    private NodesCatalogCache(CatalogClient catalogClient,
                              QueryOptions queryOptions,
                              int watchSeconds,
                              Scheduler callbackScheduler) {
        super(Node::getNode,
              (index, callback) -> {
                  checkWatch(catalogClient.getNetworkTimeoutConfig().getClientReadTimeoutMillis(), watchSeconds);
                  catalogClient.getNodes(watchParams(index, watchSeconds, queryOptions), callback);
              },
              catalogClient.getConfig().getCacheConfig(),
              catalogClient.getEventHandler(),
              new CacheDescriptor("catalog.nodes"),
              callbackScheduler);
    }

    public static NodesCatalogCache newCache(
            final CatalogClient catalogClient,
            final QueryOptions queryOptions,
            final int watchSeconds,
            final ScheduledExecutorService callbackExecutorService) {

        Scheduler scheduler = createExternal(callbackExecutorService);
        return new NodesCatalogCache(catalogClient, queryOptions, watchSeconds, scheduler);
    }

    public static NodesCatalogCache newCache(
            final CatalogClient catalogClient,
            final QueryOptions queryOptions,
            final int watchSeconds) {
        return new NodesCatalogCache(catalogClient, queryOptions, watchSeconds, createDefault());
    }

    public static NodesCatalogCache newCache(final CatalogClient catalogClient) {
        CacheConfig cacheConfig = catalogClient.getConfig().getCacheConfig();
        int watchSeconds = Ints.checkedCast(cacheConfig.getWatchDuration().getSeconds());
        return newCache(catalogClient, QueryOptions.BLANK, watchSeconds);
    }

}
