package org.kiwiproject.consul.cache;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.consul.Awaiting.awaitAtMost500ms;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.BaseIntegrationTest;
import org.kiwiproject.consul.model.State;
import org.kiwiproject.consul.model.health.ServiceHealth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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

        agentClient.register(9090, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        awaitAtMost500ms().until(() -> serviceHasPassingCheck(serviceId));

        try (var cache = ServiceHealthCache.newCache(healthClient, serviceName)) {
            cache.start();
            cache.awaitInitialized(3, TimeUnit.SECONDS);

            ServiceHealthKey serviceKey = getServiceHealthKeyFromCache(cache, serviceId, 9090)
                    .orElseThrow(() -> new RuntimeException("Cannot find service key from serviceHealthCache"));

            ServiceHealth health = cache.getMap().get(serviceKey);
            assertThat(health).isNotNull();
            assertThat(health.getService().getId()).isEqualTo(serviceId);

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
            assertThat(healthMap).hasSize(2);
            ServiceHealth health = healthMap.get(serviceKey1);
            ServiceHealth health2 = healthMap.get(serviceKey2);

            assertThat(health).isNotNull();
            assertThat(health.getService().getId()).isEqualTo(serviceId);

            assertThat(health2).isNotNull();
            assertThat(health2.getService().getId()).isEqualTo(serviceId2);
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

            assertThat(events).isEmpty();

            cache.start();
            cache.awaitInitialized(1000, TimeUnit.MILLISECONDS);

            assertThat(events).hasSize(1);

            agentClient.deregister(serviceId);

            awaitAtMost500ms().until(() -> events.size() == 2);

            Map<ServiceHealthKey, ServiceHealth> event0 = events.get(0);

            assertThat(event0).hasSize(1);
            for (Map.Entry<ServiceHealthKey, ServiceHealth> kv : event0.entrySet()) {
                assertThat(serviceId).isEqualTo(kv.getKey().getServiceId());
            }

            Map<ServiceHealthKey, ServiceHealth> event1 = events.get(1);
            assertThat(event1).isEmpty();
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

            assertThat(events).hasSize(1);
            Map<ServiceHealthKey, ServiceHealth> event0 = events.get(0);
            assertThat(event0).isEmpty();
        }
    }

    @Test
    void shouldNotifyLateListenersRaceCondition() throws Exception {
        var serviceId = createAutoDeregisterServiceId();
        var serviceName = randomUUIDString();
        var executor = Executors.newSingleThreadExecutor();

        try (var cache = ServiceHealthCache.newCache(client.healthClient(), serviceName)) {
            var earlyCount = new AtomicInteger();
            var lateCount  = new AtomicInteger();
            var lateAdded = new CountDownLatch(1);
            var lateFired = new CountDownLatch(1);

            cache.addListener(newValues -> {
                earlyCount.incrementAndGet();
                executor.submit(() -> {
                    cache.addListener(newValues1 -> {
                        lateCount.incrementAndGet();
                        lateFired.countDown();
                    });
                    lateAdded.countDown();
                });
            });

            cache.start();
            cache.awaitInitialized(1, TimeUnit.SECONDS);

            // Force a new event so the late listener definitely gets one.
            // The following registers a new service (initially in "critical" state)
            // and then makes the health check as "passing", triggering a change event.
            agentClient.register(60_090, 10L, serviceName, serviceId, List.of(), Map.of());
            agentClient.pass(serviceId);

            assertThat(lateAdded.await(2, TimeUnit.SECONDS))
                    .describedAs("late listener added")
                    .isTrue();

            assertThat(lateFired.await(2, TimeUnit.SECONDS))
                    .describedAs("late listener registered")
                    .isTrue();

            assertThat(earlyCount)
                    .describedAs("early listener fired at least once")
                    .hasValueGreaterThanOrEqualTo(1);

            assertThat(lateCount)
                    .describedAs("late listener fired at least once")
                    .hasValueGreaterThanOrEqualTo(1);

            cache.stop();
        } finally {
            executor.shutdownNow();
        }
    }
}
