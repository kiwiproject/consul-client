package org.kiwiproject.consul.config;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.kiwiproject.consul.Awaiting.awaitAtMost500ms;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.consul.cache.CacheDescriptor;
import org.kiwiproject.consul.cache.ConsulCache;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.monitoring.ClientEventHandler;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

class CacheConfigTest {

    @Test
    void testDefaults() {
        var config = CacheConfig.builder().build();
        assertThat(config.getMinimumBackOffDelay()).isEqualTo(CacheConfig.DEFAULT_BACKOFF_DELAY);
        assertThat(config.getMaximumBackOffDelay()).isEqualTo(CacheConfig.DEFAULT_BACKOFF_DELAY);
        assertThat(config.getWatchDuration()).isEqualTo(CacheConfig.DEFAULT_WATCH_DURATION);
        assertThat(config.getMinimumDurationBetweenRequests()).isEqualTo(CacheConfig.DEFAULT_MIN_DELAY_BETWEEN_REQUESTS);
        assertThat(config.getMinimumDurationDelayOnEmptyResult()).isEqualTo(CacheConfig.DEFAULT_MIN_DELAY_ON_EMPTY_RESULT);
        assertThat(config.isTimeoutAutoAdjustmentEnabled()).isEqualTo(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED);
        assertThat(config.getTimeoutAutoAdjustmentMargin()).isEqualTo(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN);

        var loggedAsWarn = new AtomicBoolean(false);
        var logger = mock(Logger.class);
        doAnswer(vars -> {
            loggedAsWarn.set(true);
            return null;
        }).when(logger).error(anyString(), any(Throwable.class));
        config.getRefreshErrorLoggingConsumer().accept(logger, "some string", new Throwable("oop"));
        assertThat(loggedAsWarn.get()).as("Should have logged as warning").isTrue();
    }

    @Test
    void shouldNotPermitNegativeWatchDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CacheConfig.builder().withWatchDuration(Duration.ofSeconds(-1)).build())
                .withMessage("Delay must be positive");
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideBackOffDelay(Duration backOffDelay) {
        var config = CacheConfig.builder().withBackOffDelay(backOffDelay).build();
        assertThat(config.getMinimumBackOffDelay()).isEqualTo(backOffDelay);
        assertThat(config.getMaximumBackOffDelay()).isEqualTo(backOffDelay);
    }

    @Test
    void shouldNotPermitNegativeBackOffDelay() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CacheConfig.builder().withBackOffDelay(Duration.ofSeconds(-1)).build())
                .withMessage("Delay must be positive");
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideMinDelayBetweenRequests(Duration delayBetweenRequests) {
        var config = CacheConfig.builder().withMinDelayBetweenRequests(delayBetweenRequests).build();
        assertThat(config.getMinimumDurationBetweenRequests()).isEqualTo(delayBetweenRequests);
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideMinDelayOnEmptyResult(Duration delayBetweenRequests) {
        var config = CacheConfig.builder().withMinDelayOnEmptyResult(delayBetweenRequests).build();
        assertThat(config.getMinimumDurationDelayOnEmptyResult()).isEqualTo(delayBetweenRequests);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testOverrideTimeoutAutoAdjustmentEnabled(boolean enabled) {
        var config = CacheConfig.builder().withTimeoutAutoAdjustmentEnabled(enabled).build();
        assertThat(config.isTimeoutAutoAdjustmentEnabled()).isEqualTo(enabled);
    }

    @ParameterizedTest(name = "Margin: {0}")
    @MethodSource("getDurationSamples")
    void testOverrideTimeoutAutoAdjustmentMargin(Duration margin) {
        var config = CacheConfig.builder().withTimeoutAutoAdjustmentMargin(margin).build();
        assertThat(config.getTimeoutAutoAdjustmentMargin()).isEqualTo(margin);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testOverrideRefreshErrorLogConsumer(boolean logLevelWarning) {
        var config = logLevelWarning
                ? CacheConfig.builder().withRefreshErrorLoggedAsWarning().build()
                : CacheConfig.builder().withRefreshErrorLoggedAsError().build();

        var logged = new AtomicBoolean(false);
        var loggedAsWarn = new AtomicBoolean(false);
        var logger = mock(Logger.class);
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
        var loggedAsDebug = new AtomicBoolean(false);
        var logger = mock(Logger.class);
        doAnswer(vars -> {
            loggedAsDebug.set(true);
            return null;
        }).when(logger).debug(anyString(), any(Throwable.class));

        var config = CacheConfig.builder().withRefreshErrorLoggedAs(Logger::debug).build();
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
            var config = CacheConfig.builder().withBackOffDelay(minDelay, maxDelay).build();
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
        var responseSupplier = new TestCacheSupplier(0, Duration.ofMillis(100));

        var cacheConfig = CacheConfig.builder()
                .withMinDelayOnEmptyResult(Duration.ofMillis(100))
                .build();

        try (var cache = TestCache.createCache(cacheConfig, responseSupplier)) {
            cache.start();

            awaitAtMost500ms().until(() -> responseSupplier.run > 0);
        }
    }

    @Test
    void testMinDelayOnEmptyResultWithResults() {
        var responseSupplier = new TestCacheSupplier(1, Duration.ofMillis(50));

        var cacheConfig = CacheConfig.builder()
                .withMinDelayOnEmptyResult(Duration.ofMillis(100))
                .withMinDelayBetweenRequests(Duration.ofMillis(50)) // do not blow ourselves up
                .build();

        try (var cache = TestCache.createCache(cacheConfig, responseSupplier)) {
            cache.start();
            awaitAtMost500ms().until(() -> responseSupplier.run > 0);
        }
    }


    static class TestCache extends ConsulCache<Integer, Integer> {

        private TestCache(Function<Integer, Integer> keyConversion,
                CallbackConsumer<Integer> callbackConsumer,
                CacheConfig cacheConfig,
                ClientEventHandler eventHandler,
                CacheDescriptor cacheDescriptor) {

            super(keyConversion, callbackConsumer, cacheConfig, eventHandler, cacheDescriptor);
        }

        static TestCache createCache(CacheConfig config, Supplier<List<Integer>> responseSupplier) {
            var clientEventHandler = mock(ClientEventHandler.class);
            var cacheDescriptor = new CacheDescriptor("test", "test");

            final CallbackConsumer<Integer> callbackConsumer = (index, callback) ->
                    callback.onComplete(new ConsulResponse<>(responseSupplier.get(), 0, true, BigInteger.ZERO, null, null));

            return new TestCache((i) -> i,
                    callbackConsumer,
                    config,
                    clientEventHandler,
                    cacheDescriptor);
        }
    }

    static class TestCacheSupplier implements Supplier<List<Integer>> {

        private final int resultCount;
        private final Duration expectedInterval;

        // mutable fields
        private int run;
        private LocalTime lastCall;

        TestCacheSupplier(int resultCount, Duration expectedInterval) {
            this.resultCount = resultCount;
            this.expectedInterval = expectedInterval;
        }

        @Override
        public List<Integer> get() {
            if (nonNull(lastCall)) {
                long millisBetween = Duration.between(lastCall, LocalTime.now()).toMillis();
                assertThat(Math.abs(millisBetween - expectedInterval.toMillis()))
                        .describedAs("expected duration between calls of %d, got %s", expectedInterval.toMillis(), millisBetween)
                        .isLessThan(20);
            }
            lastCall = LocalTime.now();
            run++;

            return Collections.nCopies(resultCount, 1);
        }
    }
}
