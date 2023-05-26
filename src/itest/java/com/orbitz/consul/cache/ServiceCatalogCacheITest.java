package com.orbitz.consul.cache;

import static com.orbitz.consul.Awaiting.awaitAtMost1s;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;

import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.model.catalog.CatalogService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

class ServiceCatalogCacheITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = List.of();
    private static final Map<String, String> NO_META = Map.of();

    @Test
    void testWatchService() throws InterruptedException {
        String name = randomUUIDString();
        String serviceId1 = createAutoDeregisterServiceId();
        String serviceId2 = createAutoDeregisterServiceId();

        List<Map<String, CatalogService>> result = new CopyOnWriteArrayList<>();

        ServiceCatalogCache cache = ServiceCatalogCache.newCache(client.catalogClient(), name);
        cache.addListener(serviceMap -> {
            System.out.println("ServiceCatalogCacheITest: listener received: " + serviceMap);
            result.add(serviceMap);
        });

        cache.start();
        cache.awaitInitialized(3, TimeUnit.SECONDS);

        client.agentClient().register(20001, 20, name, serviceId1, NO_TAGS, NO_META);
        client.agentClient().register(20002, 20, name, serviceId2, NO_TAGS, NO_META);

        awaitAtMost1s().until(() -> servicesAreRegistered(name, serviceId1, serviceId2));

        await().atMost(FIVE_SECONDS).until(() -> {
            System.out.println("ServiceCatalogCacheITest: result.size() at " + System.currentTimeMillis() + " = " + result.size());
            return result.size() == 3;
        });

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

    private boolean servicesAreRegistered(String serviceName, String... serviceIds) {
        List<String> registeredServiceIds = client.catalogClient().getService(serviceName)
                .getResponse()
                .stream()
                .map(CatalogService::getServiceId)
                .collect(toList());

        System.out.println("ServiceCatalogCacheITest: serviceIds: " + Arrays.toString(serviceIds));
        System.out.println("ServiceCatalogCacheITest: registeredServiceIds: " + registeredServiceIds);

        return registeredServiceIds.size() == serviceIds.length &&
                registeredServiceIds.containsAll(List.of(serviceIds));
    }
}
