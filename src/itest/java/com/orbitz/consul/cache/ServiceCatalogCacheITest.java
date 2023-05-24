package com.orbitz.consul.cache;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.model.catalog.CatalogService;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class ServiceCatalogCacheITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = List.of();
    private static final Map<String, String> NO_META = Map.of();

    @Test
    public void testWatchService() throws InterruptedException {
        String name = UUID.randomUUID().toString();
        String serviceId1 = createAutoDeregisterServiceId();
        String serviceId2 = createAutoDeregisterServiceId();

        List<Map<String, CatalogService>> result = new CopyOnWriteArrayList<>();

        ServiceCatalogCache cache = ServiceCatalogCache.newCache(client.catalogClient(), name);
        cache.addListener(result::add);

        cache.start();
        cache.awaitInitialized(3, TimeUnit.SECONDS);

        client.agentClient().register(20001, 20, name, serviceId1, NO_TAGS, NO_META);
        client.agentClient().register(20002, 20, name, serviceId2, NO_TAGS, NO_META);

        await().atMost(TWO_SECONDS).until(() -> result.size() == 3);

        assertEquals(0, result.get(0).size());
        assertEquals(1, result.get(1).size());
        assertEquals(2, result.get(2).size());

        assertTrue(result.get(1).containsKey(serviceId1));
        assertFalse(result.get(1).containsKey(serviceId2));

        assertTrue(result.get(2).containsKey(serviceId1));
        assertTrue(result.get(2).containsKey(serviceId2));

        assertEquals(serviceId1, result.get(1).get(serviceId1).getServiceId());
        assertEquals(serviceId2, result.get(2).get(serviceId2).getServiceId());
    }
}
