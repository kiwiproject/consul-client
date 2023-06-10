package org.kiwiproject.consul.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.consul.Awaiting.awaitAtMost500ms;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.BaseIntegrationTest;
import org.kiwiproject.consul.model.catalog.CatalogService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

class ServiceCatalogCacheITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = List.of();
    private static final Map<String, String> NO_META = Map.of();

    @Test
    void testWatchService() throws InterruptedException {
        var serviceName = randomUUIDString();
        var serviceId1 = createAutoDeregisterServiceId();
        var serviceId2 = createAutoDeregisterServiceId();

        List<Map<String, CatalogService>> result = new CopyOnWriteArrayList<>();

        try (var cache = ServiceCatalogCache.newCache(client.catalogClient(), serviceName)) {
            cache.addListener(result::add);

            cache.start();
            cache.awaitInitialized(3, TimeUnit.SECONDS);
            client.agentClient().register(20001, 20, serviceName, serviceId1, NO_TAGS, NO_META);
            awaitAtMost500ms().until(() -> serviceIsRegistered(serviceName, serviceId1));

            client.agentClient().register(20002, 20, serviceName, serviceId2, NO_TAGS, NO_META);
            awaitAtMost500ms().until(() -> serviceIsRegistered(serviceName, serviceId1));
        }

        assertThat(result).hasSize(3);

        assertThat(result.get(0)).isEmpty();
        assertThat(result.get(1)).hasSize(1);
        assertThat(result.get(2)).hasSize(2);

        assertThat(result.get(1)).containsKey(serviceId1);
        assertThat(result.get(1)).doesNotContainKey(serviceId2);

        assertThat(result.get(2)).containsKey(serviceId1);
        assertThat(result.get(2)).containsKey(serviceId2);

        assertThat(result.get(1).get(serviceId1).getServiceId()).isEqualTo(serviceId1);
        assertThat(result.get(2).get(serviceId2).getServiceId()).isEqualTo(serviceId2);
    }

    private boolean serviceIsRegistered(String serviceName, String serviceId) {
        return client.catalogClient().getService(serviceName)
                .getResponse()
                .stream()
                .anyMatch(catalogService -> catalogService.getServiceId().equals(serviceId));
    }
}
