package com.orbitz.consul.cache;

import static com.orbitz.consul.Awaiting.awaitAtMost500ms;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.Synchroniser;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.config.ClientConfig;
import com.orbitz.consul.model.kv.Value;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

class KVCacheITest extends BaseIntegrationTest {

    private Consul consulClient;
    private KeyValueClient kvClient;

    @BeforeEach
    void setUp() {
        consulClient = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withClientConfiguration(new ClientConfig(CacheConfig.builder().withWatchDuration(Duration.ofSeconds(1)).build()))
                .withReadTimeoutMillis(Duration.ofSeconds(11).toMillis())
                .withConnectTimeoutMillis(Duration.ofMillis(500).toMillis())
                .withWriteTimeoutMillis(Duration.ofMillis(500).toMillis())
                .build();

        kvClient = consulClient.keyValueClient();
    }

    @Test
    void nodeCacheKvTest() throws Exception {
        var root = randomUUIDString();

        for (int i = 0; i < 5; i++) {
            kvClient.putValue(root + "/" + i, String.valueOf(i));
        }

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            cache.start();

            if (!cache.awaitInitialized(1, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }

            ImmutableMap<String, Value> map = cache.getMap();
            for (int i = 0; i < 5; i++) {
                var keyStr = String.format("%s/%s", root, i);
                var valStr = String.valueOf(i);
                assertEquals(valStr, map.get(keyStr).getValueAsString().get());
            }

            for (int i = 0; i < 5; i++) {
                if (i % 2 == 0) {
                    kvClient.putValue(root + "/" + i, String.valueOf(i * 10));
                }
            }

            awaitAtMost500ms().until(() -> cache.getMap().size() == 5);

            map = cache.getMap();
            for (int i = 0; i < 5; i++) {
                String keyStr = String.format("%s/%s", root, i);
                String valStr = i % 2 == 0 ? "" + (i * 10) : String.valueOf(i);
                assertEquals(valStr, map.get(keyStr).getValueAsString().get());
            }
        }

        kvClient.deleteKeys(root);
    }

    @Test
    void testListeners() throws Exception {
        var root = randomUUIDString();
        final List<Map<String, Value>> events = new ArrayList<>();

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            cache.addListener(events::add);
            cache.start();

            if (!cache.awaitInitialized(1, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }

            for (int keyIdx = 0; keyIdx < 5; keyIdx++) {
                kvClient.putValue(String.format("%s/%s", root, keyIdx), String.valueOf(keyIdx));
                Synchroniser.pause(Duration.ofMillis(100));
            }
        }

        assertEquals(6, events.size());
        for (int eventIdx = 1; eventIdx < 6; eventIdx++) {
            Map<String, Value> map = events.get(eventIdx);
            assertEquals(eventIdx, map.size());

            for (int keyIdx = 0; keyIdx < eventIdx; keyIdx++) {
                Optional<String> value = map
                        .get(String.format("%s/%s", root, keyIdx))
                        .getValueAsString();

                if (!value.isPresent()) {
                    fail(String.format("Missing value for event %s and key %s", eventIdx, keyIdx));
                }
                assertEquals(String.valueOf(keyIdx), value.get());
            }
        }

        kvClient.deleteKeys(root);
    }

    @Test
    void testLateListenersGetValues() throws Exception {
        var root = randomUUIDString();

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            cache.start();

            if (!cache.awaitInitialized(1, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }

            final List<Map<String, Value>> events = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                kvClient.putValue(root + "/" + i, String.valueOf(i));
                Synchroniser.pause(Duration.ofMillis(100));
            }

            cache.addListener(events::add);
            assertEquals(1, events.size());

            Map<String, Value> map = events.get(0);
            assertEquals(5, map.size());
            for (int j = 0; j < 5; j++) {
                var keyStr = String.format("%s/%s", root, j);
                var valStr = String.valueOf(j);
                assertEquals(valStr, map.get(keyStr).getValueAsString().get());
            }
        }

        kvClient.deleteKeys(root);
    }

    @Test
    void testListenersNonExistingKeys() throws Exception {
        var root = randomUUIDString();

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            final List<Map<String, Value>> events = new ArrayList<>();
            cache.addListener(events::add);
            cache.start();

            if (!cache.awaitInitialized(1, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }

            awaitAtMost500ms().until(() -> events.size() == 1);

            assertEquals(1, events.size());
            Map<String, Value> map = events.get(0);
            assertEquals(0, map.size());
        }
    }

    @Test
    void testLifeCycleDoubleStart() throws Exception {
        var root = randomUUIDString();

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            assertEquals(ConsulCache.State.LATENT, cache.getState());
            cache.start();
            assertThat(cache.getState(), anyOf(is(ConsulCache.State.STARTING), is(ConsulCache.State.STARTED)));

            if (!cache.awaitInitialized(10, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }
            assertEquals(ConsulCache.State.STARTED, cache.getState());

            assertThrows(IllegalStateException.class, () -> cache.start());
        }
    }

    @Test
    void testLifeCycle() throws Exception {
        var root = randomUUIDString();
        final List<Map<String, Value>> events = new ArrayList<>();

        // intentionally not using try-with-resources to test the cache lifecycle methods
        var cache = KVCache.newCache(kvClient, root, 10);

        try {
            cache.addListener(events::add);
            assertEquals(ConsulCache.State.LATENT, cache.getState());

            cache.start();
            assertThat(cache.getState(), anyOf(is(ConsulCache.State.STARTING), is(ConsulCache.State.STARTED)));

            if (!cache.awaitInitialized(1, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }
            assertEquals(ConsulCache.State.STARTED, cache.getState());

            for (int i = 0; i < 5; i++) {
                kvClient.putValue(root + "/" + i, String.valueOf(i));
                Synchroniser.pause(Duration.ofMillis(100));
            }
            assertEquals(6, events.size());

            cache.stop();
            assertEquals(ConsulCache.State.STOPPED, cache.getState());

            // now assert that we get no more update to the listener
            for (int i = 0; i < 5; i++) {
                kvClient.putValue(root + "/" + i + "-again", String.valueOf(i));
                Synchroniser.pause(Duration.ofMillis(100));
            }

            assertEquals(6, events.size());
        } finally {
            // verify stop is idempotent
            cache.stop();
            assertEquals(ConsulCache.State.STOPPED, cache.getState());
        }

        kvClient.deleteKeys(root);
    }

    @Test
    void ensureCacheInitialization() throws InterruptedException {
        var key = randomUUIDString();
        var value = randomUUIDString();
        kvClient.putValue(key, value);

        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        try (var cache = KVCache.newCache(kvClient, key, (int)Duration.ofSeconds(1).getSeconds())) {
            cache.addListener(values -> {
                success.set(isValueEqualsTo(values, value));
                completed.countDown();
            });

            cache.start();
            completed.await(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            kvClient.deleteKey(key);
        }

        assertTrue(success.get());
    }

    @ParameterizedTest(name = "queries of {0} seconds")
    @MethodSource("getBlockingQueriesDuration")
    void checkUpdateNotifications(int queryDurationSec) throws InterruptedException {
        var scheduledExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("kvcache-itest-%d").build()
        );

        var key = randomUUIDString();
        var value = randomUUIDString();
        var newValue = randomUUIDString();
        kvClient.putValue(key, value);

        final CountDownLatch completed = new CountDownLatch(2);
        final AtomicBoolean success = new AtomicBoolean(false);

        try (var cache = KVCache.newCache(kvClient, key, queryDurationSec)) {
            cache.addListener(values -> {
                success.set(isValueEqualsTo(values, newValue));
                completed.countDown();
            });

            cache.start();
            scheduledExecutor.schedule(() -> kvClient.putValue(key, newValue), 3, TimeUnit.SECONDS);
            completed.await(4, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            kvClient.deleteKey(key);
            scheduledExecutor.shutdownNow();
        }

        assertTrue(success.get());
    }

    static Stream<Arguments> getBlockingQueriesDuration() {
        return Stream.of(
            arguments(new Object[] { 1 }),
            arguments(new Object[] { 10 })
        );
    }

    private boolean isValueEqualsTo(Map<String, Value> values, String expectedValue) {
        var value = values.get("");
        if (value == null) {
            return false;
        }
        Optional<String> valueAsString = value.getValueAsString();
        return valueAsString.isPresent() && expectedValue.equals(valueAsString.get());
    }
}