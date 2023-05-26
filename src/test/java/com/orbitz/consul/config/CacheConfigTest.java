package com.orbitz.consul.config;

import static com.orbitz.consul.Awaiting.awaitAtMost500ms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.orbitz.consul.cache.CacheDescriptor;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.monitoring.ClientEventHandler;
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
        assertThat(config.getMinimumBackOffDelay()).isEqualTo(CacheConfig.DEFAULT_BACKOFF_DELAY);
        assertThat(config.getMaximumBackOffDelay()).isEqualTo(CacheConfig.DEFAULT_BACKOFF_DELAY);
        assertThat(config.getWatchDuration()).isEqualTo(CacheConfig.DEFAULT_WATCH_DURATION);
        assertThat(config.getMinimumDurationBetweenRequests()).isEqualTo(CacheConfig.DEFAULT_MIN_DELAY_BETWEEN_REQUESTS);
        assertThat(config.getMinimumDurationDelayOnEmptyResult()).isEqualTo(CacheConfig.DEFAULT_MIN_DELAY_ON_EMPTY_RESULT);
        assertThat(config.isTimeoutAutoAdjustmentEnabled()).isEqualTo(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED);
        assertThat(config.getTimeoutAutoAdjustmentMargin()).isEqualTo(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN);

        AtomicBoolean loggedAsWarn = new AtomicBoolean(false);
        Logger logger = mock(Logger.class);
        doAnswer(vars -> {
            loggedAsWarn.set(true);
            return null;
        }).when(logger).error(anyString(), any(Throwable.class));
        config.getRefreshErrorLoggingConsumer().accept(logger, "some string", new Throwable("oop"));
        assertThat(loggedAsWarn.get()).as("Should have logged as warning").isTrue();
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideBackOffDelay(Duration backOffDelay) {
        CacheConfig config = CacheConfig.builder().withBackOffDelay(backOffDelay).build();
        assertThat(config.getMinimumBackOffDelay()).isEqualTo(backOffDelay);
        assertThat(config.getMaximumBackOffDelay()).isEqualTo(backOffDelay);
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideMinDelayBetweenRequests(Duration delayBetweenRequests) {
        CacheConfig config = CacheConfig.builder().withMinDelayBetweenRequests(delayBetweenRequests).build();
        assertThat(config.getMinimumDurationBetweenRequests()).isEqualTo(delayBetweenRequests);
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideMinDelayOnEmptyResult(Duration delayBetweenRequests) {
        CacheConfig config = CacheConfig.builder().withMinDelayOnEmptyResult(delayBetweenRequests).build();
        assertThat(config.getMinimumDurationDelayOnEmptyResult()).isEqualTo(delayBetweenRequests);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testOverrideTimeoutAutoAdjustmentEnabled(boolean enabled) {
        CacheConfig config = CacheConfig.builder().withTimeoutAutoAdjustmentEnabled(enabled).build();
        assertThat(config.isTimeoutAutoAdjustmentEnabled()).isEqualTo(enabled);
    }

    @ParameterizedTest(name = "Margin: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideTimeoutAutoAdjustmentMargin(Duration margin) {
        CacheConfig config = CacheConfig.builder().withTimeoutAutoAdjustmentMargin(margin).build();
        assertThat(config.getTimeoutAutoAdjustmentMargin()).isEqualTo(margin);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testOverrideRefreshErrorLogConsumer(boolean logLevelWarning) {
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
        assertThat(logged.get()).isTrue();
        assertThat(loggedAsWarn.get()).isEqualTo(logLevelWarning);
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
        assertThat(loggedAsDebug.get()).isTrue();
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
                fail("", String.format("Should not be able to build cache with min retry delay %d ms and max retry delay %d ms",
                        minDelay.toMillis(), maxDelay.toMillis()));
            }
            assertThat(config.getMinimumBackOffDelay()).isEqualTo(minDelay);
            assertThat(config.getMaximumBackOffDelay()).isEqualTo(maxDelay);
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
    void testMinDelayOnEmptyResultWithNoResults() {
        TestCacheSupplier res = new TestCacheSupplier(0, Duration.ofMillis(100));

        try (TestCache cache = TestCache.createCache(CacheConfig.builder()
                .withMinDelayOnEmptyResult(Duration.ofMillis(100))
                .build(), res)) {
            cache.start();

            awaitAtMost500ms().until(() -> res.run > 0);
        }
    }

    @Test
    void testMinDelayOnEmptyResultWithResults() {
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

            final CallbackConsumer<Integer> callbackConsumer = (index, callback) ->
                    callback.onComplete(new ConsulResponse<>(res.get(), 0, true, BigInteger.ZERO, null, null));

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
        private final Duration expectedInterval;
        private LocalTime lastCall;

        TestCacheSupplier(int resultCount, Duration expectedInterval) {
            this.resultCount = resultCount;
            this.expectedInterval = expectedInterval;
        }

        @Override
        public List<Integer> get() {
            if (lastCall != null) {
                long between = Duration.between(lastCall, LocalTime.now()).toMillis();
                assertThat(Math.abs(between - expectedInterval.toMillis()))
                        .describedAs(String.format("expected duration between calls of %d, got %s", expectedInterval.toMillis(), between))
                        .isLessThan(20);
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
