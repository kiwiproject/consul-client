package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.consul.util.failover.strategy.ConsulFailoverStrategy;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@DisplayName("Consul")
class ConsulTest {

    @Nested
    class WithMultipleHostAndPort {

        @ParameterizedTest
        @MethodSource("org.kiwiproject.consul.ConsulTest#multipleHostAndPortArguments")
        void shouldRequireAtLeastTwoHosts(Collection<HostAndPort> hosts) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Consul.builder().withMultipleHostAndPort(hosts, 5_000));
        }

        @ParameterizedTest
        @ValueSource(longs = {-42L, -1L})
        void shouldNotAllowNegativeBlacklistTime(long blacklistTimeoutMillis) {
            var hosts = List.of(
                HostAndPort.fromString("consul1.acme.com:8500"),
                HostAndPort.fromString("consul2.acme.com:8500")
            );

            var expectedMessage = "Blacklist time must be positive (or zero)";
            assertAll(
                    () -> assertThatIllegalArgumentException()
                            .isThrownBy(() -> Consul.builder().withMultipleHostAndPort(hosts,
                                    blacklistTimeoutMillis).build())
                            .withMessage(expectedMessage),

                    () -> assertThatIllegalArgumentException()
                            .isThrownBy(() -> Consul.builder().withMultipleHostAndPort(hosts,
                                    Duration.ofMillis(blacklistTimeoutMillis)).build())
                            .withMessage(expectedMessage),

                    () -> assertThatIllegalArgumentException()
                            .isThrownBy(() -> Consul.builder().withMultipleHostAndPort(hosts,
                                    blacklistTimeoutMillis, TimeUnit.MILLISECONDS).build())
                            .withMessage(expectedMessage)
            );
        }
    }

    static Stream<Arguments> multipleHostAndPortArguments() {
        return Stream.of(
            arguments(List.<HostAndPort>of()),
            arguments(List.of(HostAndPort.fromString("consul1.acme.com:8500")))
        );
    }

    @Nested
    class WithFailoverInterceptor {

        @Test
        void shouldRequireFailoverStrategy() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Consul.builder().withFailoverInterceptor(null))
                    .withMessage("Must not provide a null strategy");
        }
    }

    @Nested
    class WithMultipleFailoverInterceptors {

        @Test
        void shouldDetectWhenConsulInterceptorAlreadySetBy_withMultipleHostAndPort() {
            var hosts = List.of(
                    HostAndPort.fromString("consul1.acme.com:8500"),
                    HostAndPort.fromString("consul2.acme.com:8500")
            );
            var consulBuilder = Consul.builder()
                    .withMultipleHostAndPort(hosts, 10_000)
                    .withFailoverInterceptor(mock(ConsulFailoverStrategy.class));

            assertThat(consulBuilder.numTimesConsulFailoverInterceptorSet()).isEqualTo(2);
        }

        @Test
        void shouldDetectWhenConsulInterceptorAlreadySetBy_withFailoverInterceptor() {
            var hosts = List.of(
                    HostAndPort.fromString("consul1.acme.com:8500"),
                    HostAndPort.fromString("consul2.acme.com:8500")
            );
            var consulBuilder = Consul.builder()
                    .withFailoverInterceptor(mock(ConsulFailoverStrategy.class))
                    .withMultipleHostAndPort(hosts, 7_500);

            assertThat(consulBuilder.numTimesConsulFailoverInterceptorSet()).isEqualTo(2);
        }
    }
}
