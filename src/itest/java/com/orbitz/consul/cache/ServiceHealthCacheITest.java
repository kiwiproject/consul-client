package com.orbitz.consul.cache;

import static com.orbitz.consul.Awaiting.awaitAtMost1s;
import static com.orbitz.consul.Awaiting.awaitAtMost500ms;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.Objects.isNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.ImmutableMap;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.model.State;
import com.orbitz.consul.model.health.ServiceHealth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ServiceHealthCacheITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = List.of();
    private static final Map<String, String> NO_META = Map.of();

    private AgentClient agentClient;

    @BeforeEach
    void setUp() {
        agentClient = client.agentClient();
    }

    @Test
    void nodeCacheServicePassingTest() throws Exception {
        var healthClient = client.healthClient();
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        awaitAtMost500ms().until(() -> serviceHasPassingCheck(serviceId));

        try (var cache = ServiceHealthCache.newCache(healthClient, serviceName)) {
            cache.start();
            cache.awaitInitialized(3, TimeUnit.SECONDS);

            ServiceHealthKey serviceKey = getServiceHealthKeyFromCache(cache, serviceId, 8080)
                    .orElseThrow(() -> new RuntimeException("Cannot find service key from serviceHealthCache"));

            ServiceHealth health = cache.getMap().get(serviceKey);
            assertNotNull(health);
            assertEquals(serviceId, health.getService().getId());

            agentClient.fail(serviceId);

            awaitAtMost500ms().until(() -> isNull(cache.getMap().get(serviceKey)));
        }
    }

    private boolean serviceHasPassingCheck(String serviceId) {
        return agentClient.getChecks()
            .values()
            .stream()
            .anyMatch(check ->
                check.getCheckId().equals("service:" + serviceId) && State.fromName(check.getStatus()) == State.PASS);
    }

    @Test
    void testServicesAreUniqueByID() throws Exception {
        var healthClient = client.healthClient();
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();
        var serviceId2 = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        agentClient.register(8080, 20L, serviceName, serviceId2, NO_TAGS, NO_META);
        agentClient.pass(serviceId2);

        try (var cache = ServiceHealthCache.newCache(healthClient, serviceName)) {
            cache.start();
            cache.awaitInitialized(3, TimeUnit.SECONDS);

            ServiceHealthKey serviceKey1 = getServiceHealthKeyFromCache(cache, serviceId, 8080)
                    .orElseThrow(() -> new RuntimeException("Cannot find service key 1 from serviceHealthCache"));

            ServiceHealthKey serviceKey2 = getServiceHealthKeyFromCache(cache, serviceId2, 8080)
                    .orElseThrow(() -> new RuntimeException("Cannot find service key 2 from serviceHealthCache"));

            ImmutableMap<ServiceHealthKey, ServiceHealth> healthMap = cache.getMap();
            assertEquals(2, healthMap.size());
            ServiceHealth health = healthMap.get(serviceKey1);
            ServiceHealth health2 = healthMap.get(serviceKey2);

            assertEquals(serviceId, health.getService().getId());
            assertEquals(serviceId2, health2.getService().getId());
        }
    }

    private static Optional<ServiceHealthKey> getServiceHealthKeyFromCache(ServiceHealthCache cache, String serviceId, int port) {
        return cache.getMap().keySet()
                .stream()
                .filter(key -> serviceId.equals(key.getServiceId()) && (port == key.getPort()))
                .findFirst();
    }

    @Test
    void shouldNotifyListener() throws Exception {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        try (var cache = ServiceHealthCache.newCache(client.healthClient(), serviceName)) {
            final List<Map<ServiceHealthKey, ServiceHealth>> events = new ArrayList<>();
            cache.addListener(events::add);

            assertEquals(0, events.size());

            cache.start();
            cache.awaitInitialized(1000, TimeUnit.MILLISECONDS);

            assertEquals(1, events.size());

            agentClient.deregister(serviceId);

            awaitAtMost500ms().until(() -> events.size() == 2);

            Map<ServiceHealthKey, ServiceHealth> event0 = events.get(0);

            assertEquals(1, event0.size());
            for (Map.Entry<ServiceHealthKey, ServiceHealth> kv : event0.entrySet()) {
                assertEquals(kv.getKey().getServiceId(), serviceId);
            }

            Map<ServiceHealthKey, ServiceHealth> event1 = events.get(1);
            assertEquals(0, event1.size());
        }
    }

    @Test
    void shouldNotifyLateListenersIfNoService() throws Exception {
        var serviceName = randomUUIDString();

        try (ServiceHealthCache cache = ServiceHealthCache.newCache(client.healthClient(), serviceName)) {
            final List<Map<ServiceHealthKey, ServiceHealth>> events = new ArrayList<>();
            cache.addListener(events::add);

            cache.start();
            cache.awaitInitialized(1000, TimeUnit.MILLISECONDS);

            assertEquals(1, events.size());
            Map<ServiceHealthKey, ServiceHealth> event0 = events.get(0);
            assertEquals(0, event0.size());
        }
    }

    @Test
    void shouldNotifyLateListenersRaceCondition() throws Exception {
        var serviceName = randomUUIDString();

        try (var cache = ServiceHealthCache.newCache(client.healthClient(), serviceName)) {
            final var eventCount = new AtomicInteger(0);
            cache.addListener(newValues -> {
                eventCount.incrementAndGet();
                Thread t = new Thread(() -> cache.addListener(newValues1 -> eventCount.incrementAndGet()));
                t.start();
            });

            cache.start();
            cache.awaitInitialized(1000, TimeUnit.MILLISECONDS);

            awaitAtMost1s().until(() -> eventCount.get() == 2);

            cache.stop();
        }
    }
}
