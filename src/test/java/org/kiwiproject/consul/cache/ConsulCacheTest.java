package org.kiwiproject.consul.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Runnables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.kiwiproject.consul.TestUtils;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.cache.ConsulCache.CallbackConsumer;
import org.kiwiproject.consul.cache.ConsulCache.Scheduler;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.kv.ImmutableValue;
import org.kiwiproject.consul.model.kv.Value;
import org.kiwiproject.consul.monitoring.ClientEventHandler;
import org.kiwiproject.consul.option.ConsistencyMode;
import org.kiwiproject.consul.option.ImmutableQueryOptions;
import org.kiwiproject.consul.option.Options;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

    @Nested
    class ScheduleRunCallbackSafely  {
        
        private Scheduler scheduler;
        private CacheDescriptor descriptor;
        private Runnable callback;

        @BeforeEach
        void setUp() {
            scheduler = mock(Scheduler.class);
            descriptor = new CacheDescriptor("testEndpoint", "testKey");
            callback = Runnables.doNothing();
        }
        
        @Test
        void shouldNotSchedule_WheCacheIsNotRunning() {
            var result = ConsulCache.DefaultConsulResponseCallback.scheduleRunCallbackSafely(
                    false, descriptor, scheduler, 500, callback);
            assertThat(result).isFalse();

            verifyNoInteractions(scheduler);
        }

        @Test
        void shouldIgnoreRejectedExecutionExceptions() {
            var delayMillis = ThreadLocalRandom.current().nextLong(100, 1000);
            doThrow(new RejectedExecutionException("I reject you!"))
                    .when(scheduler)
                    .schedule(callback, delayMillis, TimeUnit.MILLISECONDS);

            var result = ConsulCache.DefaultConsulResponseCallback.scheduleRunCallbackSafely(
                    true, descriptor, scheduler, delayMillis, callback);
            assertThat(result).isFalse();

            verify(scheduler, only()).schedule(callback, delayMillis, TimeUnit.MILLISECONDS);
        }

        @Test
        void shouldSchedule() {
            var delayMillis = ThreadLocalRandom.current().nextLong(100, 1000);

            var result = ConsulCache.DefaultConsulResponseCallback.scheduleRunCallbackSafely(
                    true, descriptor, scheduler, delayMillis, callback);
            assertThat(result).isTrue();

            verify(scheduler, only()).schedule(callback, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void testWatchParamsWithNoAdditionalOptions() {
        var index = new BigInteger("12");
        var expectedQueryOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .build();
        var actualQueryOptions = ConsulCache.watchParams(index, 10, Options.BLANK_QUERY_OPTIONS);
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

        var value = newSampleValue();
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

        var value = newSampleValue();
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
            var key = value.getKey();
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

        var value = newSampleValue();
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
                var key = value.getKey();
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

        var value = newSampleValue();
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

    @Nested
    class StopIfRunningQuietly {

        private Stopwatch stopwatch;

        @BeforeEach
        void setUp() {
            stopwatch = Stopwatch.createStarted();
        }

        @Test
        void shouldStopWhenRunning() {
            var wasStopped = ConsulCache.stopIfRunningQuietly(stopwatch);
            assertThat(wasStopped).isTrue();
            assertThat(stopwatch.isRunning()).isFalse();
        }

        @Test
        void shouldDoNothingWhenAlreadyStopped() {
            stopwatch.stop();

            var wasStopped = ConsulCache.stopIfRunningQuietly(stopwatch);
            assertThat(wasStopped).isFalse();
            assertThat(stopwatch.isRunning()).isFalse();
        }

        @Test
        void shouldIgnoreIllegalStateDuringRaceCondition_WhenIsRunningReturnsTrue_ButWasAlreadyStopped() {
            var stopwatchSpy = spy(stopwatch);

            // Simulate race condition by stopping the Stopwatch, but
            // tell the spy to return true from isRunning.
            stopwatchSpy.stop();
            doReturn(true).when(stopwatchSpy).isRunning();

            var wasStopped = ConsulCache.stopIfRunningQuietly(stopwatchSpy);
            assertThat(wasStopped).isFalse();
        }
    }

    @Test
    void shouldReturnEmptyMap_WhenLastResponseIsNull() {
        Function<Value, String> keyExtractor = Value::getKey;

        var cacheConfig = CacheConfig.builder().build();
        var eventHandler = mock(ClientEventHandler.class);

        var value = newSampleValue();
        List<Value> result = List.of(value);
        var callbackConsumer = new StubCallbackConsumer(result);

        try (var cache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""))) {
            assertThat(cache.getMap()).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            HIT, 0
            HIT, 5
            HIT, 5000
            """)
    void shouldPopulateLastCacheInfoWhenResponseContainsCacheHitHeaders(String cacheHeader, String ageHeader) {
        Function<Value, String> keyConversion = value -> TestUtils.randomUUIDString();

        var value = newSampleValue();
        List<Value> result = List.of(value);
        var callbackConsumer = new StubCallbackConsumer(result, cacheHeader, ageHeader);

        var cacheConfig = CacheConfig.builder().withMinDelayBetweenRequests(Duration.ofMillis(20)).build();
        var eventHandler = new ClientEventHandler("test", null);
        var descriptor = new CacheDescriptor("test.endpoint");

        try (var cache = new ConsulCache<>(keyConversion, callbackConsumer, cacheConfig, eventHandler, descriptor)) {
            cache.start();
            await().atMost(FIVE_SECONDS).until(() -> cache.awaitInitialized(100, TimeUnit.MILLISECONDS));

            ConsulResponse<ImmutableMap<String, Value>> respWithMeta = cache.getMapWithMetadata();

            var cacheInfoOpt = respWithMeta.getCacheResponseInfo();
            assertThat(cacheInfoOpt).isPresent();

            var cacheInfo = cacheInfoOpt.orElseThrow();
            assertThat(cacheInfo.isCacheHit()).isTrue();
            assertThat(cacheInfo.getAgeInSeconds()).hasValue(Long.valueOf(ageHeader));
        }
    }

    @Test
    void shouldPopulateLastCacheInfoWhenResponseContainsCacheMissHeaders() {
        Function<Value, String> keyConversion = value -> TestUtils.randomUUIDString();

        var value = newSampleValue();
        List<Value> result = List.of(value);
        var cacheHeader = "MISS";
        var callbackConsumer = new StubCallbackConsumer(result, cacheHeader, null);

        var cacheConfig = CacheConfig.builder().withMinDelayBetweenRequests(Duration.ofMillis(15)).build();
        var eventHandler = new ClientEventHandler("test", null);
        var descriptor = new CacheDescriptor("test.endpoint");

        try (var cache = new ConsulCache<>(keyConversion, callbackConsumer, cacheConfig, eventHandler, descriptor)) {
            cache.start();
            await().atMost(FIVE_SECONDS).until(() -> cache.awaitInitialized(100, TimeUnit.MILLISECONDS));

            ConsulResponse<ImmutableMap<String, Value>> respWithMeta = cache.getMapWithMetadata();

            var cacheInfoOpt = respWithMeta.getCacheResponseInfo();
            assertThat(cacheInfoOpt).isPresent();

            var cacheInfo = cacheInfoOpt.orElseThrow();
            assertThat(cacheInfo.isCacheHit()).isFalse();
            assertThat(cacheInfo.getAgeInSeconds()).isEmpty();
        }
    }

    @Test
    void shouldHaveEmptyLastCacheInfoWhenResponseDoesNotContainCacheHeaders() {
        Function<Value, String> keyConversion = value -> TestUtils.randomUUIDString();

        var value = newSampleValue();
        List<Value> result = List.of(value);
        var callbackConsumer = new StubCallbackConsumer(result);

        var cacheConfig = CacheConfig.builder().withMinDelayBetweenRequests(Duration.ofMillis(15)).build();
        var eventHandler = new ClientEventHandler("test", null);
        var descriptor = new CacheDescriptor("test.endpoint");

        try (var cache = new ConsulCache<>(keyConversion, callbackConsumer, cacheConfig, eventHandler, descriptor)) {
            cache.start();
            await().atMost(FIVE_SECONDS).until(() -> cache.awaitInitialized(100, TimeUnit.MILLISECONDS));

            ConsulResponse<ImmutableMap<String, Value>> respWithMeta = cache.getMapWithMetadata();

            assertThat(respWithMeta.getCacheResponseInfo()).isEmpty();
        }
    }

    @Test
    void onFailure_in_DefaultConsulResponseCallback_shouldReportErrorAndScheduleWhenRunning() {
        // Arrange deterministic backoff and a no-op error logger we can spy on
        var errorLogger = spy(CacheConfig.RefreshErrorLogConsumer.class);

        var delay = Duration.ofMillis(250);
        var cacheConfig = CacheConfig.builder()
                .withBackOffDelay(delay)
                .withRefreshErrorLoggedAs(errorLogger)
                .build();

        // Provide a do-nothing default to the spy, so calling accept() works
        doAnswer(invocation -> null).when(errorLogger).accept(any(), anyString(), any());

        var eventHandler = mock(ClientEventHandler.class);
        var scheduler = mock(Scheduler.class);
        var cacheDescriptor = new CacheDescriptor("/kv", "test");

        // CallbackConsumer that always fails
        var exception = new RuntimeException("boom");
        CallbackConsumer<Value> failingConsumer = (index, callback) -> callback.onFailure(exception);

        Function<Value, String> keyExtractor = Value::getKey;

        try (var cache = new ConsulCache<>(keyExtractor, failingConsumer, cacheConfig, eventHandler, cacheDescriptor, scheduler)) {
            cache.start();

            // Verify event handler notified
            var timeoutMillis = timeout(500);
            verify(eventHandler, timeoutMillis)
                    .cachePollingError(same(cacheDescriptor), any(RuntimeException.class));

            // Verify callback was scheduled with the configured backoff delay
            verify(scheduler, timeoutMillis)
                    .schedule(any(Runnable.class), eq(delay.toMillis()), eq(TimeUnit.MILLISECONDS));

            // Verify the logging consumer was invoked
            var expectedErrorMessage = String.format("Error getting response from consul for %s, will retry in %d %s",
                    cacheDescriptor,
                    delay.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            verify(errorLogger, atLeastOnce())
                    .accept(any(), eq(expectedErrorMessage), same(exception));
        }
    }

    @Test
    void onFailure_in_DefaultConsulResponseCallback_shouldDoNothingWhenNotRunning() {
        Function<Value, String> keyExtractor = Value::getKey;
        var delay = Duration.ofMillis(100);
        var cacheConfig = CacheConfig.builder().withBackOffDelay(delay).build();
        var eventHandler = mock(ClientEventHandler.class);
        var scheduler = mock(Scheduler.class);
        var cacheDescriptor = new CacheDescriptor("/kv", "test");

        var callbackHolder = new AtomicReference<ConsulResponseCallback<List<Value>>>();
        CallbackConsumer<Value> capturingConsumer = (index, callback) -> callbackHolder.set(callback);

        try (var cache = new ConsulCache<>(keyExtractor, capturingConsumer, cacheConfig, eventHandler, cacheDescriptor, scheduler)) {
            cache.start();
            assertThat(callbackHolder.get())
                    .describedAs("precondition: callback should be captured after start()")
                    .isNotNull();

            // Stop cache so that onFailure sees a non-running state
            cache.stop();

            // Manually invoke onFailure after stopping
            callbackHolder.get().onFailure(new RuntimeException("boom"));

            // Verify no interactions since the cache is not running
            verify(eventHandler, never()).cachePollingError(any(), any());
            verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        }
    }

    private static ImmutableValue newSampleValue() {
        return ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key("foo")
                .flags(0)
                .build();
    }
}
