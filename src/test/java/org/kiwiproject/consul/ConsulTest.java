package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.List;
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
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Consul.builder().withMultipleHostAndPort(hosts, blacklistTimeoutMillis).build())
                    .withMessage("Blacklist time must be positive");
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
}
