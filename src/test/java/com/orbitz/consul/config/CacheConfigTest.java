package com.orbitz.consul.config;

import static com.orbitz.consul.Awaiting.awaitAtMost500ms;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.orbitz.consul.cache.CacheDescriptor;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.monitoring.ClientEventHandler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

class CacheConfigTest {

    @Test
    void testDefaults() {
        CacheConfig config = CacheConfig.builder().build();
        assertEquals(CacheConfig.DEFAULT_BACKOFF_DELAY, config.getMinimumBackOffDelay());
        assertEquals(CacheConfig.DEFAULT_BACKOFF_DELAY, config.getMaximumBackOffDelay());
        assertEquals(CacheConfig.DEFAULT_WATCH_DURATION, config.getWatchDuration());
        assertEquals(CacheConfig.DEFAULT_MIN_DELAY_BETWEEN_REQUESTS, config.getMinimumDurationBetweenRequests());
        assertEquals(CacheConfig.DEFAULT_MIN_DELAY_ON_EMPTY_RESULT, config.getMinimumDurationDelayOnEmptyResult());
        assertEquals(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED, config.isTimeoutAutoAdjustmentEnabled());
        assertEquals(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN, config.getTimeoutAutoAdjustmentMargin());

        AtomicBoolean loggedAsWarn = new AtomicBoolean(false);
        Logger logger = mock(Logger.class);
        doAnswer(vars -> {
            loggedAsWarn.set(true);
            return null;
        }).when(logger).error(anyString(), any(Throwable.class));
        config.getRefreshErrorLoggingConsumer().accept(logger, "some string", new Throwable("oop"));
        assertTrue(loggedAsWarn.get(), "Should have logged as warning");
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideBackOffDelay(Duration backOffDelay) {
        CacheConfig config = CacheConfig.builder().withBackOffDelay(backOffDelay).build();
        assertEquals(backOffDelay, config.getMinimumBackOffDelay());
        assertEquals(backOffDelay, config.getMaximumBackOffDelay());
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideMinDelayBetweenRequests(Duration delayBetweenRequests) {
        CacheConfig config = CacheConfig.builder().withMinDelayBetweenRequests(delayBetweenRequests).build();
        assertEquals(delayBetweenRequests, config.getMinimumDurationBetweenRequests());
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideMinDelayOnEmptyResult(Duration delayBetweenRequests) {
        CacheConfig config = CacheConfig.builder().withMinDelayOnEmptyResult(delayBetweenRequests).build();
        assertEquals(delayBetweenRequests, config.getMinimumDurationDelayOnEmptyResult());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testOverrideTimeoutAutoAdjustmentEnabled(boolean enabled) {
        CacheConfig config = CacheConfig.builder().withTimeoutAutoAdjustmentEnabled(enabled).build();
        assertEquals(enabled, config.isTimeoutAutoAdjustmentEnabled());
    }

    @ParameterizedTest(name = "Margin: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideTimeoutAutoAdjustmentMargin(Duration margin) {
        CacheConfig config = CacheConfig.builder().withTimeoutAutoAdjustmentMargin(margin).build();
        assertEquals(margin, config.getTimeoutAutoAdjustmentMargin());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testOverrideRefreshErrorLogConsumer(boolean logLevelWarning) throws InterruptedException {
        CacheConfig config = logLevelWarning
                ? CacheConfig.builder().withRefreshErrorLoggedAsWarning().build()
                : CacheConfig.builder().withRefreshErrorLoggedAsError().build();

        AtomicBoolean logged = new AtomicBoolean(false);
        AtomicBoolean loggedAsWarn = new AtomicBoolean(false);
        Logger logger = mock(Logger.class);
        doAnswer(vars -> {
            loggedAsWarn.set(true);
            logged.set(true);
            return null;
        }).when(logger).warn(anyString(), any(Throwable.class));
        doAnswer(vars -> {
            loggedAsWarn.set(false);
            logged.set(true);
            return null;
        }).when(logger).error(anyString(), any(Throwable.class));

        config.getRefreshErrorLoggingConsumer().accept(logger, "some string", new Exception("oop"));
        assertTrue(logged.get());
        assertEquals(logLevelWarning, loggedAsWarn.get());
    }

    @Test
    void testOverrideRefreshErrorLogCustom() {
        AtomicBoolean loggedAsDebug = new AtomicBoolean(false);
        Logger logger = mock(Logger.class);
        doAnswer(vars -> {
            loggedAsDebug.set(true);
            return null;
        }).when(logger).debug(anyString(), any(Throwable.class));

        CacheConfig config = CacheConfig.builder().withRefreshErrorLoggedAs(Logger::debug).build();
        config.getRefreshErrorLoggingConsumer().accept(logger, "some string", new Exception("oop"));
        assertTrue(loggedAsDebug.get());
    }

    static Stream<Arguments> getDurationSamples() {
        return Stream.of(
            arguments(Duration.ZERO),
            arguments(Duration.ofSeconds(2)),
            arguments(Duration.ofMinutes(10))
        );
    }

    @ParameterizedTest(name = "min Delay: {0}, max Delay: {1}")
    @MethodSource("getMinMaxDurationSamples")
    void testOverrideRandomBackOffDelay(Duration minDelay, Duration maxDelay, boolean isValid) {
        try {
            CacheConfig config = CacheConfig.builder().withBackOffDelay(minDelay, maxDelay).build();
            if (!isValid) {
                Assertions.fail(String.format("Should not be able to build cache with min retry delay %d ms and max retry delay %d ms",
                        minDelay.toMillis(), maxDelay.toMillis()));
            }
            assertEquals(minDelay, config.getMinimumBackOffDelay());
            assertEquals(maxDelay, config.getMaximumBackOffDelay());
        } catch (NullPointerException | IllegalArgumentException e) {
            if (isValid) {
                throw new AssertionError(String.format("Should be able to build cache with min retry delay %d ms and max retry delay %d ms",
                        minDelay.toMillis(), maxDelay.toMillis()), e);
            }
        }
    }

    static Stream<Arguments> getMinMaxDurationSamples() {
        return Stream.of(
            arguments(Duration.ZERO, Duration.ZERO, true),
            arguments(Duration.ofSeconds(2), Duration.ofSeconds(2), true),
            arguments(Duration.ZERO, Duration.ofSeconds(2), true),
            arguments(Duration.ofSeconds(2), Duration.ZERO, false),
            arguments(Duration.ofSeconds(1), Duration.ofSeconds(2), true),
            arguments(Duration.ofSeconds(2), Duration.ofSeconds(1), false),
            arguments(Duration.ofSeconds(-1), Duration.ZERO, false),
            arguments(Duration.ZERO, Duration.ofSeconds(-1), false),
            arguments(Duration.ofSeconds(-1), Duration.ofSeconds(-1), false)
        );
    }

    @Test
    void testMinDelayOnEmptyResultWithNoResults() throws InterruptedException {
        TestCacheSupplier res = new TestCacheSupplier(0, Duration.ofMillis(100));

        try (TestCache cache = TestCache.createCache(CacheConfig.builder()
                .withMinDelayOnEmptyResult(Duration.ofMillis(100))
                .build(), res)) {
            cache.start();

            awaitAtMost500ms().until(() -> res.run > 0);
        }
    }

    @Test
    void testMinDelayOnEmptyResultWithResults() throws InterruptedException {
        TestCacheSupplier res = new TestCacheSupplier(1, Duration.ofMillis(50));

        try (TestCache cache = TestCache.createCache(CacheConfig.builder()
                .withMinDelayOnEmptyResult(Duration.ofMillis(100))
                .withMinDelayBetweenRequests(Duration.ofMillis(50)) // do not blow ourselves up
                .build(), res)) {
            cache.start();
            awaitAtMost500ms().until(() -> res.run > 0);
        }
    }


    static class TestCache extends ConsulCache<Integer, Integer> {
        private TestCache(Function<Integer, Integer> keyConversion, CallbackConsumer<Integer> callbackConsumer, CacheConfig cacheConfig, ClientEventHandler eventHandler, CacheDescriptor cacheDescriptor) {
            super(keyConversion, callbackConsumer, cacheConfig, eventHandler, cacheDescriptor);
        }

        static TestCache createCache(CacheConfig config, Supplier<List<Integer>> res) {
            ClientEventHandler ev = mock(ClientEventHandler.class);
            CacheDescriptor cacheDescriptor = new CacheDescriptor("test", "test");

            final CallbackConsumer<Integer> callbackConsumer = (index, callback) -> {
                callback.onComplete(new ConsulResponse<>(res.get(), 0, true, BigInteger.ZERO, null, null));
            };

            return new TestCache((i) -> i,
                    callbackConsumer,
                    config,
                    ev,
                    cacheDescriptor);
        }
    }

    static class TestCacheSupplier implements Supplier<List<Integer>> {
        int run = 0;
        int resultCount;
        private Duration expectedInterval;
        private LocalTime lastCall;

        TestCacheSupplier(int resultCount, Duration expectedInterval) {
            this.resultCount = resultCount;
            this.expectedInterval = expectedInterval;
        }

        @Override
        public List<Integer> get() {
            if (lastCall != null) {
                long between = Duration.between(lastCall, LocalTime.now()).toMillis();
                assertTrue(Math.abs(between - expectedInterval.toMillis()) < 20,
                        String.format("expected duration between calls of %d, got %s", expectedInterval.toMillis(), between));
            }
            lastCall = LocalTime.now();
            run++;

            List<Integer> response = new ArrayList<>();
            for (int i = 0; i < resultCount; i++) {
                response.add(1);
            }
            return response;
        }
    }
}
