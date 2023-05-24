package com.orbitz.consul.cache;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.config.ClientConfig;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.Synchroniser;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.orbitz.consul.Awaiting.awaitAtMost100ms;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitParamsRunner.class)
public class KVCacheITest extends BaseIntegrationTest {

    private Consul consulClient;
    private KeyValueClient kvClient;

    @Before
    public void before() {
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
    public void nodeCacheKvTest() throws Exception {
        var root = UUID.randomUUID().toString();

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

            awaitAtMost100ms().until(() -> cache.getMap().size() == 5);

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
    public void testListeners() throws Exception {
        var root = UUID.randomUUID().toString();
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
    public void testLateListenersGetValues() throws Exception {
        var root = UUID.randomUUID().toString();

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
    public void testListenersNonExistingKeys() throws Exception {
        var root = UUID.randomUUID().toString();

        try (var cache = KVCache.newCache(kvClient, root, 10)) {
            final List<Map<String, Value>> events = new ArrayList<>();
            cache.addListener(events::add);
            cache.start();

            if (!cache.awaitInitialized(1, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }

            awaitAtMost100ms().until(() -> events.size() == 1);

            assertEquals(1, events.size());
            Map<String, Value> map = events.get(0);
            assertEquals(0, map.size());
        }
    }

    @Test
    public void testLifeCycleDoubleStart() throws Exception {
        var root = UUID.randomUUID().toString();

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
    public void testLifeCycle() throws Exception {
        var root = UUID.randomUUID().toString();
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
    public void ensureCacheInitialization() throws InterruptedException {
        var key = UUID.randomUUID().toString();
        var value = UUID.randomUUID().toString();
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

    @Test
    @Parameters(method = "getBlockingQueriesDuration")
    @TestCaseName("queries of {0} seconds")
    public void checkUpdateNotifications(int queryDurationSec) throws InterruptedException {
        var scheduledExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("kvcache-itest-%d").build()
        );

        var key = UUID.randomUUID().toString();
        var value = UUID.randomUUID().toString();
        var newValue = UUID.randomUUID().toString();
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

    public Object getBlockingQueriesDuration() {
        return new Object[] {
                new Object[] { 1 },
                new Object[] { 10 }
        };
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