package com.orbitz.consul.cache;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.ImmutableValue;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.monitoring.ClientEventHandler;
import com.orbitz.consul.option.ConsistencyMode;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.QueryOptions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

class ConsulCacheTest {

    /**
     * Test that if Consul for some reason returns a duplicate service or keyvalue entry
     * that we recover gracefully by taking the first value, ignoring duplicates, and warning
     * user of the condition
     */
    @Test
    void testDuplicateServicesDontCauseFailure() {
        final Function<Value, String> keyExtractor = input -> "SAME_KEY";
        final List<Value> response = Arrays.asList(mock(Value.class), mock(Value.class));
        CacheConfig cacheConfig = mock(CacheConfig.class);
        ClientEventHandler eventHandler = mock(ClientEventHandler.class);

        final StubCallbackConsumer callbackConsumer = new StubCallbackConsumer(List.of());

        try (var consulCache = new ConsulCache<String, Value>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {
            final ConsulResponse<List<Value>> consulResponse = new ConsulResponse<>(response, 0, false, BigInteger.ONE, null, null);
            final ImmutableMap<String, Value> map = consulCache.convertToMap(consulResponse);
            assertNotNull(map);
            // Second copy has been weeded out
            assertEquals(1, map.size());
        }
    }

    @Test
    void testWatchParamsWithNoAdditionalOptions() {
        BigInteger index = new BigInteger("12");
        QueryOptions expectedOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .build();
        QueryOptions actualOptions = ConsulCache.watchParams(index, 10, QueryOptions.BLANK);
        assertEquals(expectedOptions, actualOptions);
    }

    @Test
    void testWatchParamsWithAdditionalOptions() {
        BigInteger index = new BigInteger("12");
        QueryOptions additionalOptions = ImmutableQueryOptions.builder()
                .consistencyMode(ConsistencyMode.STALE)
                .addTag("someTag")
                .token("186596")
                .near("156892")
                .build();

        QueryOptions expectedOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .consistencyMode(ConsistencyMode.STALE)
                .addTag("someTag")
                .token("186596")
                .near("156892")
                .build();

        QueryOptions actualOptions = ConsulCache.watchParams(index, 10, additionalOptions);
        assertEquals(expectedOptions, actualOptions);
    }

    @Test
    void testWatchParamsWithAdditionalIndexAndWaitingThrows() {
        BigInteger index = new BigInteger("12");
        QueryOptions additionalOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConsulCache.watchParams(index, 10, additionalOptions));
    }

    @ParameterizedTest(name = "min Delay: {0}, max Delay: {1}")
    @MethodSource("getRetryDurationSamples")
    void testRetryDuration(Duration minDelay, Duration maxDelay) {
        CacheConfig cacheConfig = CacheConfig.builder().withBackOffDelay(minDelay, maxDelay).build();
        for (int i=0; i < 1000; i++) {
            long retryDurationMs = ConsulCache.computeBackOffDelayMs(cacheConfig);
            Assertions.assertThat(retryDurationMs)
                    .describedAs("Retry duration expected between %s and %s but got %d ms", minDelay, maxDelay, retryDurationMs)
                    .isBetween(minDelay.toMillis(), maxDelay.toMillis());
        }
    }

    static Stream<Arguments> getRetryDurationSamples() {
        return Stream.of(
            // Same duration
            arguments(Duration.ZERO, Duration.ZERO),
            arguments(Duration.ofSeconds(10), Duration.ofSeconds(10)),
            // Different durations
            arguments(Duration.ofSeconds(10), Duration.ofSeconds(11)),
            arguments(Duration.ofMillis(10), Duration.ofMinutes(1))
        );
    }

    @Test
    void testListenerIsCalled() {
        final Function<Value, String> keyExtractor = Value::getKey;

        final CacheConfig cacheConfig = CacheConfig.builder().build();
        ClientEventHandler eventHandler = mock(ClientEventHandler.class);

        final String key = "foo";
        final ImmutableValue value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        final List<Value> result = List.of(value);
        final StubCallbackConsumer callbackConsumer = new StubCallbackConsumer(result);

        try (var cache = new ConsulCache<String, Value>(keyExtractor, callbackConsumer, cacheConfig,
                eventHandler, new CacheDescriptor(""))) {
                final StubListener listener = new StubListener();

                cache.addListener(listener);
                cache.start();

                assertEquals(1, listener.getCallCount());
                assertEquals(1, callbackConsumer.getCallCount());

                final Map<String, Value> lastValues = listener.getLastValues();
                assertNotNull(lastValues);
                assertEquals(result.size(), lastValues.size());
                assertTrue(lastValues.containsKey(key));
                assertEquals(value, lastValues.get(key));
        }
    }

    @Test
    void testListenerThrowingExceptionIsIsolated() throws InterruptedException {
        final Function<Value, String> keyExtractor = Value::getKey;
        final CacheConfig cacheConfig = CacheConfig.builder()
                .withMinDelayBetweenRequests(Duration.ofSeconds(10))
                .build();
        ClientEventHandler eventHandler = mock(ClientEventHandler.class);

        final String key = "foo";
        final ImmutableValue value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        final List<Value> result = List.of(value);
        try (final AsyncCallbackConsumer callbackConsumer = new AsyncCallbackConsumer(result)) {
            try (final ConsulCache<String, Value> cache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig,
                        eventHandler, new CacheDescriptor(""))) {

                final StubListener goodListener = new StubListener();
                final AlwaysThrowsListener badListener1 = new AlwaysThrowsListener();

                cache.addListener(badListener1);
                cache.addListener(goodListener);
                cache.start();

                await().atMost(FIVE_SECONDS).until(() -> goodListener.getCallCount() > 0);

                assertEquals(1, goodListener.getCallCount());
                assertEquals(1, callbackConsumer.getCallCount());

                final Map<String, Value> lastValues = goodListener.getLastValues();
                assertNotNull(lastValues);
                assertEquals(result.size(), lastValues.size());
                assertTrue(lastValues.containsKey(key));
                assertEquals(value, lastValues.get(key));
            }
        }
    }

    @Test
    void testExceptionReceivedFromListenerWhenAlreadyStarted() {
        final Function<Value, String> keyExtractor = Value::getKey;
        final CacheConfig cacheConfig = CacheConfig.builder()
                .withMinDelayBetweenRequests(Duration.ofSeconds(10))
                .build();
        final ClientEventHandler eventHandler = mock(ClientEventHandler.class);

        final String key = "foo";
        final ImmutableValue value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        final List<Value> result = List.of(value);
        final StubCallbackConsumer callbackConsumer = new StubCallbackConsumer(
                result);

        try (final ConsulCache<String, Value> cache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig,
                eventHandler, new CacheDescriptor(""))) {

            final AlwaysThrowsListener badListener = new AlwaysThrowsListener();

            cache.start();

            // Adding listener after cache is already started
            final boolean isAdded = cache.addListener(badListener);
            assertTrue(isAdded);

            final StubListener goodListener = new StubListener();
            cache.addListener(goodListener);

            assertEquals(1, goodListener.getCallCount());
        }
    }

}
