package com.orbitz.consul.cache;

import static com.orbitz.consul.Awaiting.awaitAtMost500ms;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
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

    private KeyValueClient kvClient;

    @BeforeEach
    void setUp() {
        Consul consulClient = Consul.builder()
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

            if (cacheNotInitializedWithinOneSecond(cache)) {
                fail("cache initialization failed");
            }

            ImmutableMap<String, Value> map = cache.getMap();
            for (int i = 0; i < 5; i++) {
                var keyStr = String.format("%s/%s", root, i);
                var valStr = String.valueOf(i);

                var value = map.get(keyStr);
                assertThat(value).isNotNull();
                assertThat(value.getValueAsString()).contains(valStr);
            }

            for (int i = 0; i < 5; i++) {
                if (i % 2 == 0) {
                    kvClient.putValue(root + "/" + i, String.valueOf(i * 10));
                }
            }

            awaitAtMost500ms().until(() -> cache.getMap().size() == 5);

            map = cache.getMap();
            for (int i = 0; i < 5; i++) {
                var keyStr = String.format("%s/%s", root, i);
                var valStr = i % 2 == 0 ? String.valueOf(i * 10) : String.valueOf(i);

                var value = map.get(keyStr);
                assertThat(value).isNotNull();
                assertThat(value.getValueAsString()).contains(valStr);
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

            if (cacheNotInitializedWithinOneSecond(cache)) {
                fail("cache initialization failed");
            }

            for (int keyIdx = 0; keyIdx < 5; keyIdx++) {
                kvClient.putValue(String.format("%s/%s", root, keyIdx), String.valueOf(keyIdx));
                Synchroniser.pause(Duration.ofMillis(100));
            }
        }

        assertThat(events).hasSize(6);

        for (int eventIdx = 1; eventIdx < 6; eventIdx++) {
            Map<String, Value> map = events.get(eventIdx);
            assertThat(map).hasSize(eventIdx);

            for (int keyIdx = 0; keyIdx < eventIdx; keyIdx++) {
                Optional<String> value = map
                        .get(String.format("%s/%s", root, keyIdx))
                        .getValueAsString();

                if (value.isEmpty()) {
                    fail("", String.format("Missing value for event %s and key %s", eventIdx, keyIdx));
                }
                assertThat(value).contains(String.valueOf(keyIdx));
            }
        }

        kvClient.deleteKeys(root);
    }

    @Test
    void testLateListenersGetValues() throws Exception {
        var root = randomUUIDString();

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            cache.start();

            if (cacheNotInitializedWithinOneSecond(cache)) {
                fail("cache initialization failed");
            }

            final List<Map<String, Value>> events = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                kvClient.putValue(root + "/" + i, String.valueOf(i));
                Synchroniser.pause(Duration.ofMillis(100));
            }

            cache.addListener(events::add);
            assertThat(events).hasSize(1);

            Map<String, Value> map = events.get(0);
            assertThat(map).hasSize(5);
            for (int j = 0; j < 5; j++) {
                var keyStr = String.format("%s/%s", root, j);
                var valStr = String.valueOf(j);
                assertThat(map.get(keyStr).getValueAsString()).contains(valStr);
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

            if (cacheNotInitializedWithinOneSecond(cache)) {
                fail("cache initialization failed");
            }

            awaitAtMost500ms().until(() -> events.size() == 1);

            assertThat(events).hasSize(1);
            Map<String, Value> map = events.get(0);
            assertThat(map).isEmpty();
        }
    }

    @Test
    void testLifeCycleDoubleStart() throws Exception {
        var root = randomUUIDString();

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            assertThat(cache.getState()).isEqualTo(ConsulCache.State.LATENT);
            cache.start();
            assertThat(cache.getState()).isIn(ConsulCache.State.STARTING, ConsulCache.State.STARTED);

            if (cacheNotInitializedWithinSeconds(cache, 10)) {
                fail("cache initialization failed");
            }
            assertThat(cache.getState()).isEqualTo(ConsulCache.State.STARTED);

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(cache::start);
        }
    }

    @Test
    void testLifeCycle() throws Exception {
        var root = randomUUIDString();
        final List<Map<String, Value>> events = new ArrayList<>();

        // intentionally not using try-with-resources to test the cache lifecycle methods
        // noinspection resource
        var cache = KVCache.newCache(kvClient, root, 10);

        try {
            cache.addListener(events::add);
            assertThat(cache.getState()).isEqualTo(ConsulCache.State.LATENT);

            cache.start();
            assertThat(cache.getState()).isIn(ConsulCache.State.STARTING, ConsulCache.State.STARTED);

            if (cacheNotInitializedWithinOneSecond(cache)) {
                fail("cache initialization failed");
            }
            assertThat(cache.getState()).isEqualTo(ConsulCache.State.STARTED);

            for (int i = 0; i < 5; i++) {
                kvClient.putValue(root + "/" + i, String.valueOf(i));
                Synchroniser.pause(Duration.ofMillis(100));
            }
            assertThat(events).hasSize(6);

            cache.stop();
            assertThat(cache.getState()).isEqualTo(ConsulCache.State.STOPPED);

            // now assert that we get no more update to the listener
            for (int i = 0; i < 5; i++) {
                kvClient.putValue(root + "/" + i + "-again", String.valueOf(i));
                Synchroniser.pause(Duration.ofMillis(100));
            }

            assertThat(events).hasSize(6);
        } finally {
            // verify stop is idempotent
            cache.stop();
            assertThat(cache.getState()).isEqualTo(ConsulCache.State.STOPPED);
        }

        kvClient.deleteKeys(root);
    }

    private boolean cacheNotInitializedWithinOneSecond(KVCache cache) {
        return cacheNotInitializedWithinSeconds(cache, 1);
    }

    private boolean cacheNotInitializedWithinSeconds(KVCache cache, long timeout) {
        try {
            return !cache.awaitInitialized(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void ensureCacheInitialization() {
        var key = randomUUIDString();
        var value = randomUUIDString();
        kvClient.putValue(key, value);

        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        try (var cache = KVCache.newCache(kvClient, key, (int) Duration.ofSeconds(1).getSeconds())) {
            cache.addListener(values -> {
                success.set(isValueEqualsTo(values, value));
                completed.countDown();
            });

            cache.start();
            completed.await(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("", e.getMessage());
        } finally {
            kvClient.deleteKey(key);
        }

        assertThat(success.get()).isTrue();
    }

    @ParameterizedTest(name = "queries of {0} seconds")
    @MethodSource("getBlockingQueriesDuration")
    void checkUpdateNotifications(int queryDurationSec) {
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
            fail(e.getMessage(), e);
        } finally {
            kvClient.deleteKey(key);
            scheduledExecutor.shutdownNow();
        }

        assertThat(success.get()).isTrue();
    }

    static Stream<Arguments> getBlockingQueriesDuration() {
        return Stream.of(
                arguments(1),
                arguments(10)
        );
    }

    private boolean isValueEqualsTo(Map<String, Value> values, String expectedValue) {
        var value = values.get("");
        if (isNull(value)) {
            return false;
        }
        Optional<String> valueAsString = value.getValueAsString();
        return valueAsString.isPresent() && expectedValue.equals(valueAsString.get());
    }
}
