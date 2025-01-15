package org.kiwiproject.consul.util.failover.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.google.common.net.HostAndPort;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@DisplayName("RoundRobinConsulFailoverStrategy")
class RoundRobinConsulFailoverStrategyTest {

    private List<HostAndPort> targets;
    private RoundRobinConsulFailoverStrategy strategy;

    @BeforeEach
    void setUp() {
        targets = List.of(
                HostAndPort.fromParts("10.116.42.1", 8501),
                HostAndPort.fromParts("10.116.42.2", 8501),
                HostAndPort.fromParts("10.116.42.3", 8501)
        );

        strategy = new RoundRobinConsulFailoverStrategy(targets);
    }

    @Nested
    class Constructors {

        @ParameterizedTest
        @NullAndEmptySource
        void shouldRequireNonEmptyTargetsCollection(List<HostAndPort> invalidTargets) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RoundRobinConsulFailoverStrategy(invalidTargets))
                    .withMessage("targets must not be null or empty");
        }

        @Test
        void shouldRequireNonNullDelayDuration() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RoundRobinConsulFailoverStrategy(targets, null))
                    .withMessage("delayAfterFailure must not be null");
        }

        @ParameterizedTest
        @ValueSource(longs = { -1000, -10, -5, -2, -1 })
        void shouldRequireZeroOrPositiveDelay(long delayMillis) {
            var delay = Duration.ofMillis(delayMillis);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RoundRobinConsulFailoverStrategy(targets, delay))
                    .withMessage("delayAfterFailure must be zero or a positive duration");
        }
    }

    @Nested
    class ComputeNextStage {

        @Test
        void shouldReturnFirstTarget_IfRequestSucceeds() {
            var request = newRequest("https://10.116.42.1:8501/v1/agent/members");

            var nextRequestHttpUrl = strategy.computeNextStage(request)
                    .map(Request::url)
                    .orElseThrow();

            assertThat(nextRequestHttpUrl).isEqualTo(request.url());
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, 1, 2 })
        void shouldReturnSameTarget_IfRequestSucceeds(int index) {
            var target = targets.get(index);
            var request = newMembersRequest(target);

            var nextRequestHttpUrl = strategy.computeNextStage(request)
                    .map(Request::url)
                    .orElseThrow();

            assertThat(nextRequestHttpUrl).isEqualTo(request.url());
        }

        @Test
        void shouldReturnSecondTarget_IfFirstRequestFails() {
            var request = newRequest("https://10.116.42.1:8501/v1/agent/members");

            strategy.markRequestFailed(request);

            var nextRequestHttpUrl = strategy.computeNextStage(request)
                    .map(Request::url)
                    .orElseThrow();

            assertThat(nextRequestHttpUrl.url())
                    .hasToString("https://10.116.42.2:8501/v1/agent/members");
        }

        @Test
        void shouldReturnExpectedTarget_AfterRequestFailure_AndStopAfterGoingThroughAllTargets() {
            var request1 = newRequest("https://10.116.42.1:8501/v1/agent/members");

            // need to mark initial request as failed (but not on later calls
            // because computeNextStage advances the index when needed)
            strategy.markRequestFailed(request1);

            var request2 = strategy.computeNextStage(request1).orElseThrow();
            assertThat(request2.url())
                    .hasToString("https://10.116.42.2:8501/v1/agent/members");

            var request3 = strategy.computeNextStage(request2).orElseThrow();
            assertThat(request3.url())
                    .hasToString("https://10.116.42.3:8501/v1/agent/members");

            var request4 = strategy.computeNextStage(request3);
             assertThat(request4)
                    .describedAs("we should get an empty Request once we've tried all targets")
                    .isEmpty();
        }

        @Nested
        class WithDelayAfterFailure {

            private int delayMillis;

            @BeforeEach
            void setUp() {
                delayMillis = 25;
                strategy = new RoundRobinConsulFailoverStrategy(targets, Duration.ofMillis(delayMillis));
            }

            @Test
            void shouldNotSleep_IfRequestSucceeds() {
                var request = newRequest("https://10.116.42.1:8501/v1/agent/members");

                var elapsedMillis = timeComputeNextStage(request);
                assertThat(elapsedMillis).isLessThan(delayMillis);
            }

            @Test
            void shouldSleep_ExpectedTime_IfRequestFails() {
                var request = newRequest("https://10.116.42.1:8501/v1/agent/members");
                strategy.markRequestFailed(request);

                var elapsedMillis = timeComputeNextStage(request);
                assertThat(elapsedMillis).isGreaterThanOrEqualTo(delayMillis);
            }

            private long timeComputeNextStage(Request request) {
                var start = System.nanoTime();
                strategy.computeNextStage(request);
                var elapsed = System.nanoTime() - start;
                return TimeUnit.NANOSECONDS.toMillis(elapsed);
            }
        }
    }

    @Nested
    class IsRequestViable {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://10.116.42.1:8501/v1/agent/members",
                "https://10.116.42.2:8501/v1/agent/members",
                "https://10.116.42.3:8501/v1/agent/members"
        })
        void shouldReturnTrueFromIsRequestViable(String url) {
            var request = newRequest(url);
            assertThat(strategy.isRequestViable(request)).isTrue();
        }

        @Test
        void shouldAlwaysReturnTrue() {
            int numTimesThroughAllTargets = 10;
            var urlTemplate = "https://10.116.42.%d:8501/v1/agent/members";

            for (var i = 0; i < numTimesThroughAllTargets; i++) {
                for (var lastOctet = 1; lastOctet <= 3; lastOctet++) {
                    var url = String.format(urlTemplate, lastOctet);
                    var request = newRequest(url);
                    assertThat(strategy.isRequestViable(request)).isTrue();
                }
            }
        }
    }

    @Nested
    class MarkRequestFailed {

        @ParameterizedTest
        @ValueSource(ints = { 0, 1, 2 })
        void shouldSetLastTargetIndex_ToIndexOfHostAndPortFromRequest(int index) {
            var target = targets.get(index);
            var request = newMembersRequest(target);

            strategy.markRequestFailed(request);

            assertThat(strategy.lastTargetIndexThreadLocal.get()).hasValue(index);
        }
    }

    private static Request newMembersRequest(HostAndPort target) {
        var url = membersUrl(target);
        return newRequest(url);
    }

    private static String membersUrl(HostAndPort target) {
        return String.format("https://%s:8501/v1/agent/members", target.getHost());
    }

    private static Request newRequest(String url) {
        return new Request.Builder().url(url).build();
    }
}
