package org.kiwiproject.consul.util.failover.strategy;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HostAndPort;
import okhttp3.Request;
import okhttp3.Response;

import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

@DisplayName("BlacklistingConsulFailoverStrategy")
class BlacklistingConsulFailoverStrategyTest {

    private BlacklistingConsulFailoverStrategy strategy;

    @Nested
    class ComputeNextStage {

        @BeforeEach
        void setUp() {
            var targets = List.of(
                HostAndPort.fromParts("1.2.3.4", 8501),
                HostAndPort.fromParts("localhost", 8501)
            );
            var longTimeout = Duration.ofSeconds(100).toMillis();

            strategy = new BlacklistingConsulFailoverStrategy(targets, longTimeout);
        }

        @Test
        void getFirstUrlBack() {
            var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
            Response previousResponse = null;

            // noinspection ConstantValue
            Optional<Request> result = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().url()).hasToString("https://1.2.3.4:8501/v1/agent/members");
        }

        @Test
        void getSecondUrlBackAfterFirstOneIsBlacklisted() {
            var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
            Response previousResponse = null;

            // noinspection ConstantValue
            Optional<Request> result1 = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(result1).isPresent();
            assertThat(result1.orElseThrow().url()).hasToString("https://1.2.3.4:8501/v1/agent/members");

            strategy.markRequestFailed(result1.get());
            // noinspection ConstantValue
            Optional<Request> result2 = strategy.computeNextStage(result1.get(), previousResponse);

            assertThat(result2).isPresent();
            assertThat(result2.orElseThrow().url()).hasToString("https://localhost:8501/v1/agent/members");
        }

        @Test
        void getNoUrlBackAfterBothAreBlacklisted() {
            var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
            Response previousResponse = null;

            // noinspection ConstantValue
            Optional<Request> result1 = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(result1).isPresent();
            assertThat(result1.orElseThrow().url()).hasToString("https://1.2.3.4:8501/v1/agent/members");

            strategy.markRequestFailed(result1.get());
            // noinspection ConstantValue
            Optional<Request> result2 = strategy.computeNextStage(result1.get(), previousResponse);

            assertThat(result2).isPresent();
            assertThat(result2.orElseThrow().url()).hasToString("https://localhost:8501/v1/agent/members");

            strategy.markRequestFailed(result2.get());

            // noinspection ConstantValue
            Optional<Request> result3 = strategy.computeNextStage(result2.get(), previousResponse);

            assertThat(result3).isEmpty();
        }

        @Test
        void shouldGetPreviousRequestUrl_WhenPreviousResponseSucceeded() {
            var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();

            var previousResponse = mock(Response.class);
            when(previousResponse.isSuccessful()).thenReturn(true);

            Optional<Request> nextRequestOptional = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(nextRequestOptional).isPresent();

            var nextRequest = nextRequestOptional.orElseThrow();
            assertThat(nextRequest.url()).hasToString("https://1.2.3.4:8501/v1/agent/members");
        }

        @Test
        void shouldGetPreviousRequestUrl_WhenPreviousResponse_Was_404_NotFound() {
            var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();

            var previousResponse = mock(Response.class);
            when(previousResponse.isSuccessful()).thenReturn(false);
            when(previousResponse.code()).thenReturn(404);

            Optional<Request> nextRequestOptional = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(nextRequestOptional).isPresent();

            var nextRequest = nextRequestOptional.orElseThrow();
            assertThat(nextRequest.url()).hasToString("https://1.2.3.4:8501/v1/agent/members");
        }

        @Test
        void shouldGetNextRequestUrl_WhenPreviousResponseFailed() {
            var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();

            var previousResponse = mock(Response.class);
            when(previousResponse.isSuccessful()).thenReturn(false);
            when(previousResponse.code()).thenReturn(500);

            Optional<Request> nextRequestOptional = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(nextRequestOptional).isPresent();

            var nextRequest = nextRequestOptional.orElseThrow();
            assertThat(nextRequest.url()).hasToString("https://localhost:8501/v1/agent/members");
        }
    }

    @Nested
    class ComputeNextStageExpiration {

        private List<HostAndPort> targets;

        @BeforeEach
        void setUp() {
            targets = List.of(
                HostAndPort.fromParts("10.116.84.1", 8501),
                HostAndPort.fromParts("10.116.84.2", 8501),
                HostAndPort.fromParts("10.116.84.3", 8501)
            );
            var timeoutInMillis = 75;

            strategy = new BlacklistingConsulFailoverStrategy(targets, timeoutInMillis);
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3 })
        void shouldReturnRequest_WhenNoTargetsAreBlacklisted(int lastOctet) {
            var url = String.format("https://10.116.84.%d:8501/v1/agent/members", lastOctet);
            var previousRequest = new Request.Builder().url(url).build();
            Response previousResponse = null;

            // noinspection ConstantValue
            Optional<Request> result = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().url()).hasToString(url);

            assertThat(strategy.blacklist).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3 })
        void shouldReturnRequest_WhenAtLeastOneTargetIsAvailable(int lastOctet) {
            var url = String.format("https://10.116.84.%d:8501/v1/agent/members", lastOctet);
            var previousRequest = new Request.Builder().url(url).build();
            Response previousResponse = null;

            randomlyBlacklistAllButOneTarget(targets, strategy);

            // noinspection ConstantValue
            Optional<Request> result = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(result).isPresent();

            var nextUrl = result.orElseThrow().url().toString();
            assertThat(nextUrl).matches("https://10.116.84.[123]:8501/v1/agent/members");

            assertThat(strategy.blacklist).hasSize(targets.size() - 1);
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3 })
        void shouldReturnRequest_WhenBlacklistTimeoutExpires(int lastOctet) {
            var url = String.format("https://10.116.84.%d:8501/v1/agent/members", lastOctet);
            var previousRequest = new Request.Builder().url(url).build();
            Response previousResponse = null;

            blacklistAll(targets);

            assertThat(strategy.blacklist).hasSameSizeAs(targets);

            //noinspection ConstantValue
            await().atMost(Durations.TWO_HUNDRED_MILLISECONDS)
                    .until(() -> strategy.computeNextStage(previousRequest, previousResponse).isPresent());

            assertThat(strategy.blacklist).hasSizeLessThan(targets.size());
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3 })
        void shouldNotReturnRequest_WhenAllTargetsAreBlacklisted(int lastOctet) {
            var url = String.format("https://10.116.84.%d:8501/v1/agent/members", lastOctet);
            var previousRequest = new Request.Builder().url(url).build();
            Response previousResponse = null;

            blacklistAll(targets);

            // noinspection ConstantValue
            Optional<Request> result = strategy.computeNextStage(previousRequest, previousResponse);

            assertThat(result).isEmpty();

            assertThat(strategy.blacklist).hasSameSizeAs(targets);
        }
    }

    @Nested
    class IsRequestViable {

        private List<HostAndPort> targets;

        @BeforeEach
        void setUp() {
            targets = List.of(
                HostAndPort.fromParts("10.116.42.1", 8501),
                HostAndPort.fromParts("10.116.42.2", 8501),
                HostAndPort.fromParts("10.116.42.3", 8501)
            );
            var timeoutInMillis = 50;

            strategy = new BlacklistingConsulFailoverStrategy(targets, timeoutInMillis);
        }

        @Test
        void shouldBeViable_WhenThereAreAvailableTargets() {
            var request = new Request.Builder().url("https://10.116.42.1:8501/v1/agent/members").build();

            assertThat(strategy.isRequestViable(request)).isTrue();

            assertThat(strategy.blacklist).isEmpty();
        }

        @Nested
        class WhenThereAreBlacklistedTargets {

            @RepeatedTest(5)
            void shouldBeViable_WhenAtLeastOneTargetIsAvailable() {
                var request = new Request.Builder().url("https://10.116.42.1:8501/v1/agent/members").build();

                randomlyBlacklistAllButOneTarget(targets, strategy);

                assertThat(strategy.isRequestViable(request)).isTrue();

                assertThat(strategy.blacklist).hasSizeLessThan(targets.size());
            }

            @Test
            void shouldBeViable_WhenBlacklistTimeoutExpires() {
                var request = new Request.Builder().url("https://10.116.42.1:8501/v1/agent/members").build();

                blacklistAll(targets);

                await().atMost(Durations.TWO_HUNDRED_MILLISECONDS)
                        .until(() -> strategy.isRequestViable(request));

                assertThat(strategy.blacklist).hasSizeLessThan(targets.size());
            }

            @Test
            void shouldNotBeViable_WhenNoTargetsAreAvailable() {
                var request = new Request.Builder().url("https://10.116.42.1:8501/v1/agent/members").build();

                blacklistAll(targets);

                assertThat(strategy.isRequestViable(request)).isFalse();

                assertThat(strategy.blacklist).hasSameSizeAs(targets);
            }
        }
    }

    @Nested
    class IsPastBlacklistDuration {

        private List<HostAndPort> targets;

        @BeforeEach
        void setUp() {
            targets = List.of(
                HostAndPort.fromParts("10.116.84.1", 8501),
                HostAndPort.fromParts("10.116.84.2", 8501),
                HostAndPort.fromParts("10.116.84.3", 8501)
            );
        }

        @RepeatedTest(5)
        void shouldReturnTrue_WhenTarget_IsNotInBlacklist() {
            strategy = new BlacklistingConsulFailoverStrategy(targets, 50);
            var index = RandomGenerator.getDefault().nextInt(0, targets.size());
            var target = targets.get(index);

            assertThat(strategy.isPastBlacklistDuration(target)).isTrue();
        }

        @RepeatedTest(5)
        void shouldReturnTrue_WhenBlacklistTimeout_HasExpired() {
            strategy = new BlacklistingConsulFailoverStrategy(targets, 5);

            var index = RandomGenerator.getDefault().nextInt(0, targets.size());
            var target = targets.get(index);

            blacklistAll(targets);

            await().pollDelay(Duration.ofMillis(5))
                    .atMost(Durations.TWO_HUNDRED_MILLISECONDS)
                    .until(() -> strategy.isPastBlacklistDuration(target));
        }

        @RepeatedTest(5)
        void shouldReturnFalse_WhenBlacklistTimeout_HasNotExpired() {
            strategy = new BlacklistingConsulFailoverStrategy(targets, 500);

            var index = RandomGenerator.getDefault().nextInt(0, targets.size());
            var target = targets.get(index);

            blacklistAll(targets);

            assertThat(strategy.isPastBlacklistDuration(target)).isFalse();
        }
    }

    private void blacklistAll(Collection<HostAndPort> targets) {
        targets.forEach(strategy::addToBlackist);
    }

    private static void randomlyBlacklistAllButOneTarget(List<HostAndPort> targets,
                                                         BlacklistingConsulFailoverStrategy strategy) {
        var numTargets = targets.size();
        var indices = IntStream.range(0, numTargets).boxed().collect(toCollection(ArrayList::new));
        Collections.shuffle(indices);

        var numToBlacklist = numTargets - 1;
        indices.subList(0, numToBlacklist).forEach(index ->
                strategy.addToBlackist(targets.get(index)));

        // guarantee we have one target available
        int numInBlacklist = strategy.blacklist.size();
        checkState(numInBlacklist == numToBlacklist,
                "expected %s in blacklist, but found %s", numToBlacklist, numInBlacklist);
    }
}
