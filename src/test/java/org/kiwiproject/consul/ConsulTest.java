package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HostAndPort;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.consul.util.failover.ConsulFailoverInterceptor;
import org.kiwiproject.consul.util.failover.strategy.ConsulFailoverStrategy;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
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

        @SuppressWarnings("removal")
        @Test
        void shouldRequireFailoverStrategy_Deprecated() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Consul.builder().withFailoverInterceptor(null))
                    .withMessage("Must not provide a null strategy");
        }

        @Test
        void shouldRequireFailoverStrategy() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Consul.builder().withFailoverInterceptorUsingStrategy(null))
                    .withMessage("Must not provide a null strategy");
        }
    }

    @Nested
    class WithConsulFailoverInterceptor {

        @Test
        void shouldRequireNonNullInterceptor() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Consul.builder().withConsulFailoverInterceptor(null))
                    .withMessage("failoverInterceptor must not be null");
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
                    .withMultipleHostAndPort(hosts, Duration.ofSeconds(10))
                    .withFailoverInterceptorUsingStrategy(mock(ConsulFailoverStrategy.class));

            assertThat(consulBuilder.numTimesConsulFailoverInterceptorSet()).isEqualTo(2);
        }

        @Test
        void shouldDetectWhenConsulInterceptorAlreadySetBy_withConsulFailoverInterceptor() {
            var failoverInterceptor = new ConsulFailoverInterceptor(mock(ConsulFailoverStrategy.class));
            var consulBuilder = Consul.builder()
                    .withConsulFailoverInterceptor(failoverInterceptor)
                    .withFailoverInterceptorUsingStrategy(mock(ConsulFailoverStrategy.class));

            assertThat(consulBuilder.numTimesConsulFailoverInterceptorSet()).isEqualTo(2);
        }

        @SuppressWarnings("removal")
        @Test
        void shouldDetectWhenConsulInterceptorAlreadySetBy_withFailoverInterceptor_Deprecated() {
            var hosts = List.of(
                    HostAndPort.fromString("consul1.acme.com:8500"),
                    HostAndPort.fromString("consul2.acme.com:8500")
            );
            var consulBuilder = Consul.builder()
                    .withFailoverInterceptor(mock(ConsulFailoverStrategy.class))
                    .withMultipleHostAndPort(hosts, 7_500_000, TimeUnit.MICROSECONDS);

            assertThat(consulBuilder.numTimesConsulFailoverInterceptorSet()).isEqualTo(2);
        }

        @Test
        void shouldDetectWhenConsulInterceptorAlreadySetBy_withFailoverInterceptorUsingStrategy() {
            var hosts = List.of(
                    HostAndPort.fromString("consul1.acme.com:8500"),
                    HostAndPort.fromString("consul2.acme.com:8500")
            );
            var consulBuilder = Consul.builder()
                    .withFailoverInterceptorUsingStrategy(mock(ConsulFailoverStrategy.class))
                    .withMultipleHostAndPort(hosts, 7_500_000, TimeUnit.MICROSECONDS);

            assertThat(consulBuilder.numTimesConsulFailoverInterceptorSet()).isEqualTo(2);
        }
    }

    @Nested
    class WithMaxFailoverAttempts {

        @ParameterizedTest
        @ValueSource(ints = { -10, -5, -1, 0 })
        void shouldRequirePositiveNumbers(int maxFailoverAttempts) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Consul.builder().withMaxFailoverAttempts(maxFailoverAttempts));
        }

        @Test
        void shouldReturnTheBuilder() {
            var builder = Consul.builder();
            assertThat(builder.withMaxFailoverAttempts(5)).isSameAs(builder);
        }
    }

    @Nested
    class AddSslSocketFactory {

        @Test
        void shouldDoNothingWhenSslContextIsNull() {
            var okHttpClientBuilder = new OkHttpClient.Builder();

            Consul.Builder.addSslSocketFactory(null, null, okHttpClientBuilder);

            assertThat(okHttpClientBuilder.getSslSocketFactoryOrNull$okhttp()).isNull();
            assertThat(okHttpClientBuilder.getX509TrustManagerOrNull$okhttp()).isNull();
        }

        @Test
        void shouldAddSocketFactoryAndDefaultTrustManager_WhenGivenSslContext() throws NoSuchAlgorithmException {
            var okHttpClientBuilder = new OkHttpClient.Builder();
            var sslContext = mock(SSLContext.class);
            var socketFactory = SSLContext.getDefault().getSocketFactory();
            when(sslContext.getSocketFactory()).thenReturn(socketFactory);

            Consul.Builder.addSslSocketFactory(sslContext, null, okHttpClientBuilder);

            assertThat(okHttpClientBuilder.getSslSocketFactoryOrNull$okhttp()).isSameAs(socketFactory);
            assertThat(okHttpClientBuilder.getX509TrustManagerOrNull$okhttp()).isNotNull();
        }

        @Test
        void shouldAddSocketFactoryAndGivenTrustManager_WhenGivenBoth() throws NoSuchAlgorithmException {
            var okHttpClientBuilder = new OkHttpClient.Builder();
            var sslContext = mock(SSLContext.class);
            var socketFactory = SSLContext.getDefault().getSocketFactory();
            when(sslContext.getSocketFactory()).thenReturn(socketFactory);

            var trustManager = new NonProductionX509TrustManager();

            Consul.Builder.addSslSocketFactory(sslContext, trustManager, okHttpClientBuilder);

            assertThat(okHttpClientBuilder.getSslSocketFactoryOrNull$okhttp()).isSameAs(socketFactory);
            assertThat(okHttpClientBuilder.getX509TrustManagerOrNull$okhttp()).isSameAs(trustManager);
        }
    }

    public static class NonProductionX509TrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
            // Trust all clients
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
            // Trust all servers
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            // Return an empty array of certificate authorities
            return new X509Certificate[0];
        }
    }
}
