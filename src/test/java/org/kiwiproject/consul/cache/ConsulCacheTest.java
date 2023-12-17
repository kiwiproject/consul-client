package org.kiwiproject.consul.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kiwiproject.consul.TestUtils;
import org.kiwiproject.consul.cache.ConsulCache.CallbackConsumer;
import org.kiwiproject.consul.cache.ConsulCache.Scheduler;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.kv.ImmutableValue;
import org.kiwiproject.consul.model.kv.Value;
import org.kiwiproject.consul.monitoring.ClientEventHandler;
import org.kiwiproject.consul.option.ConsistencyMode;
import org.kiwiproject.consul.option.ImmutableQueryOptions;
import org.kiwiproject.consul.option.QueryOptions;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

class ConsulCacheTest {

    @Nested
    class Constructors {

        private Function<Value, String> keyConversion;
        private CallbackConsumer<Value> callbackConsumer;
        private CacheConfig cacheConfig;
        private ClientEventHandler clientEventHandler;
        private CacheDescriptor cacheDescriptor;

        @BeforeEach
        void setUp() {
            keyConversion = value -> TestUtils.randomUUIDString();
            callbackConsumer = new StubCallbackConsumer(List.of());
            cacheConfig = CacheConfig.builder().build();
            clientEventHandler = new ClientEventHandler("testClient", null);
            cacheDescriptor = new CacheDescriptor("test.endpoint");
        }

        @Test
        void shouldRequireKeyConversionArg() {
            // noinspection resource
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ConsulCache<>(null, callbackConsumer, cacheConfig, clientEventHandler, cacheDescriptor));
        }

        @Test
        void shouldRequireCallbackConsumerArg() {
            // noinspection resource
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ConsulCache<>(keyConversion, null, cacheConfig, clientEventHandler, cacheDescriptor));
        }

        @Test
        void shouldRequireCacheConfigArg() {
            // noinspection resource
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ConsulCache<>(keyConversion, callbackConsumer, null, clientEventHandler, cacheDescriptor));
        }

        @Test
        void shouldRequireEventHandlerArg() {
            // noinspection resource
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ConsulCache<>(keyConversion, callbackConsumer, cacheConfig, null, cacheDescriptor));
        }

        @Test
        void shouldRequireCacheDescriptorArg() {
            // noinspection resource
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ConsulCache<>(keyConversion, callbackConsumer, cacheConfig, clientEventHandler, null));
        }

        @Test
        void shouldRequireSchedulerArg() {
            // noinspection resource
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ConsulCache<>(keyConversion, callbackConsumer, cacheConfig, clientEventHandler, cacheDescriptor, (Scheduler) null));
        }
    }

    /**
     * Test that if Consul for some reason returns a duplicate service or key/value entry
     * that we recover gracefully by taking the first value, ignoring duplicates, and warning
     * the user of the condition
     */
    @Test
    void testDuplicateServicesDontCauseFailure() {
        Function<Value, String> keyExtractor = input -> "SAME_KEY";
        List<Value> response = List.of(mock(Value.class), mock(Value.class));

        var cacheConfig = mock(CacheConfig.class);
        var eventHandler = mock(ClientEventHandler.class);
        var callbackConsumer = new StubCallbackConsumer(List.of());

        try (var consulCache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {
            ConsulResponse<List<Value>> consulResponse = new ConsulResponse<>(response, 0, false, BigInteger.ONE, null, null);
            ImmutableMap<String, Value> map = consulCache.convertToMap(consulResponse);
            assertThat(map).isNotNull();
            // The second copy has been weeded out
            assertThat(map).hasSize(1);
        }
    }

    @Nested
    class ConvertToMap {

        @ParameterizedTest
        @MethodSource("org.kiwiproject.consul.cache.ConsulCacheTest#nullAndEmptyConsulResponses")
        void shouldReturnEmptyMap_WhenResponseIsNullOrEmpty(ConsulResponse<List<Value>> consulResponse) {
            Function<Value, String> keyExtractor = input -> "service" + System.nanoTime();
            var cacheConfig = mock(CacheConfig.class);
            var eventHandler = mock(ClientEventHandler.class);
            var callbackConsumer = new StubCallbackConsumer(List.of());

            try (var consulCache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {
                ImmutableMap<String, Value> map = consulCache.convertToMap(consulResponse);
                assertThat(map).isUnmodifiable().isEmpty();
            }
        }

        @Test
        void shouldIgnoreNullKeys() {
            Function<Value, String> keyExtractor = input -> {
                var key = input.getKey();
                return Set.of("a", "c").contains(key) ? null : key;
            };
            var cacheConfig = mock(CacheConfig.class);
            var eventHandler = mock(ClientEventHandler.class);
            var callbackConsumer = new StubCallbackConsumer(List.of());
            var cacheDescriptor = new CacheDescriptor("testEndpoint");

            try (var consulCache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, cacheDescriptor)) {
                var value1 = createTestValue("a");
                var value2 = createTestValue("b");
                var value3 = createTestValue("c");
                var value4 = createTestValue("d");
                var response = List.of(value1, value2, value3, value4);
                var consulResponse = new ConsulResponse<>(response, 0, false, BigInteger.ONE, null, null);

                ImmutableMap<String, Value> map = consulCache.convertToMap(consulResponse);

                assertThat(map)
                        .isUnmodifiable()
                        .hasSize(2)
                        .containsEntry("b", value2)
                        .containsEntry("d", value4);
            }
        }

        private Value createTestValue(String key) {
            return ImmutableValue.builder()
                    .key(key)
                    .createIndex(0)
                    .modifyIndex(0)
                    .lockIndex(0)
                    .flags(0)
                    .build();
        }
    }

    static Stream<Arguments> nullAndEmptyConsulResponses() {
        return Stream.of(
                arguments((ConsulResponse<List<Value>>) null),
                arguments(new ConsulResponse<>(null, 0, false, BigInteger.ONE, null, null)),
                arguments(new ConsulResponse<>(List.of(), 0, false, BigInteger.ONE, null, null))
        );
    }

    @Test
    void testWatchParamsWithNoAdditionalOptions() {
        var index = new BigInteger("12");
        var expectedQueryOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .build();
        var actualQueryOptions = ConsulCache.watchParams(index, 10, QueryOptions.BLANK);
        assertThat(actualQueryOptions).isEqualTo(expectedQueryOptions);
    }

    @Test
    void testWatchParamsWithAdditionalOptions() {
        var index = new BigInteger("12");
        var additionalQueryOptions = ImmutableQueryOptions.builder()
                .consistencyMode(ConsistencyMode.STALE)
                .addTag("someTag")
                .token("186596")
                .near("156892")
                .build();

        var expectedQueryOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .consistencyMode(ConsistencyMode.STALE)
                .addTag("someTag")
                .token("186596")
                .near("156892")
                .build();

        var actualQueryOptions = ConsulCache.watchParams(index, 10, additionalQueryOptions);
        assertThat(actualQueryOptions).isEqualTo(expectedQueryOptions);
    }

    @Test
    void testWatchParamsWithAdditionalIndexAndWaitingThrows() {
        var index = new BigInteger("12");
        var additionalQueryOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConsulCache.watchParams(index, 10, additionalQueryOptions));
    }

    @ParameterizedTest(name = "min Delay: {0}, max Delay: {1}")
    @MethodSource("getRetryDurationSamples")
    void testRetryDuration(Duration minDelay, Duration maxDelay) {
        var cacheConfig = CacheConfig.builder().withBackOffDelay(minDelay, maxDelay).build();
        for (int i = 0; i < 1000; i++) {
            long retryDurationMs = ConsulCache.computeBackOffDelayMs(cacheConfig);
            assertThat(retryDurationMs)
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
    void shouldAddAndRemoveListeners() {
        Function<Value, String> keyExtractor = Value::getKey;

        var cacheConfig = CacheConfig.builder().build();
        var eventHandler = mock(ClientEventHandler.class);

        var key = "foo";
        var value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        List<Value> result = List.of(value);
        var callbackConsumer = new StubCallbackConsumer(result);

        try (var cache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {
            var listener1 = new StubListener();
            var listener2 = new StubListener();
            var listener3 = new StubListener();

            cache.addListener(listener1);
            assertThat(cache.getListeners())
                    .isUnmodifiable()
                    .containsExactly(listener1);

            cache.addListener(listener2);
            assertThat(cache.getListeners())
                    .isUnmodifiable()
                    .containsExactly(listener1, listener2);

            cache.addListener(listener3);
            assertThat(cache.getListeners())
                    .isUnmodifiable()
                    .containsExactly(listener1, listener2, listener3);

            assertThat(cache.removeListener(listener2)).isTrue();
            assertThat(cache.getListeners())
                    .isUnmodifiable()
                    .containsExactly(listener1, listener3);

            assertThat(cache.removeListener(listener1)).isTrue();
            assertThat(cache.getListeners())
                    .isUnmodifiable()
                    .containsExactly(listener3);

            assertThat(cache.removeListener(listener3)).isTrue();
            assertThat(cache.getListeners())
                    .isUnmodifiable()
                    .isEmpty();
        }
    }

    @Test
    void testListenerIsCalled() {
        Function<Value, String> keyExtractor = Value::getKey;

        var cacheConfig = CacheConfig.builder().build();
        var eventHandler = mock(ClientEventHandler.class);

        var key = "foo";
        var value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        List<Value> result = List.of(value);
        var callbackConsumer = new StubCallbackConsumer(result);

        try (var cache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {

            var listener = new StubListener();

            cache.addListener(listener);
            cache.start();

            assertThat(listener.getCallCount()).isEqualTo(1);

            // This used to check "equal to 1"; after switching to AssertJ, it started failing, usually
            // with the call count at 2, but sometimes more. Moving this above the listener call count
            // assertion would (almost) always work. So, as far as I can tell, this only used to work
            // due to timing, because the callbackConsumer continues to be called back! And with the
            // default duration between requests at zero, it was getting called back frequently.
            // Because the callback is called repeatedly, checking that the value is positive seems
            // to be the best option, since the actual call count various per test run.
            // Reference: https://github.com/kiwiproject/consul-client/issues/189
            assertThat(callbackConsumer.getCallCount()).isPositive();

            final Map<String, Value> lastValues = listener.getLastValues();
            assertThat(lastValues).isNotNull();
            assertThat(lastValues).hasSameSizeAs(result);
            assertThat(lastValues).containsKey(key);
            assertThat(lastValues).containsEntry(key, value);
        }
    }

    @Test
    void testListenerThrowingExceptionIsIsolated() {
        Function<Value, String> keyExtractor = Value::getKey;
        var cacheConfig = CacheConfig.builder()
                .withMinDelayBetweenRequests(Duration.ofSeconds(10))
                .build();
        var eventHandler = mock(ClientEventHandler.class);

        var key = "foo";
        var value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        List<Value> result = List.of(value);

        try (var callbackConsumer = new AsyncCallbackConsumer(result)) {
            try (var cache = new ConsulCache<>(
                    keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {

                var goodListener = new StubListener();
                var badListener1 = new AlwaysThrowsListener();

                cache.addListener(badListener1);
                cache.addListener(goodListener);
                cache.start();

                await().atMost(FIVE_SECONDS).until(() -> goodListener.getCallCount() > 0);

                assertThat(goodListener.getCallCount()).isEqualTo(1);
                assertThat(callbackConsumer.getCallCount()).isEqualTo(1);

                Map<String, Value> lastValues = goodListener.getLastValues();
                assertThat(lastValues).isNotNull();
                assertThat(lastValues).hasSameSizeAs(result);
                assertThat(lastValues).containsKey(key);
                assertThat(lastValues).containsEntry(key, value);
            }
        }
    }

    @Test
    void testExceptionReceivedFromListenerWhenAlreadyStarted() {
        Function<Value, String> keyExtractor = Value::getKey;
        var cacheConfig = CacheConfig.builder()
                .withMinDelayBetweenRequests(Duration.ofSeconds(10))
                .build();
        var eventHandler = mock(ClientEventHandler.class);

        var key = "foo";
        var value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        List<Value> result = List.of(value);
        var callbackConsumer = new StubCallbackConsumer(result);

        try (var cache = new ConsulCache<>(
                keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {

            var badListener = new AlwaysThrowsListener();

            cache.start();

            // Adding listener after cache is already started
            var wasAdded = cache.addListener(badListener);
            assertThat(wasAdded).isTrue();

            var goodListener = new StubListener();
            cache.addListener(goodListener);

            assertThat(goodListener.getCallCount()).isEqualTo(1);
        }
    }

}
