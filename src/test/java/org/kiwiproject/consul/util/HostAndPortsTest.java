package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("HostAndPorts")
class HostAndPortsTest {

    @Nested
    class HostAndPortFromOkHttpRequest {

        @ParameterizedTest
        @CsvSource(textBlock = """
                http,  10.116.84.1,       8501
                https, 10.116.84.2,       8501
                http,  consul-1.acme.com, 8500
                https, consul-2.acme.com, 8500
                """)
        void shouldCreateNewInstance(String scheme, String host, int port) {
            var httpUrl = new HttpUrl.Builder()
                    .scheme(scheme)
                    .host(host)
                    .port(port)
                    .build();
            var request = mock(Request.class);
            when(request.url()).thenReturn(httpUrl);

            var hostAndPort = HostAndPorts.hostAndPortFromOkHttpRequest(request);

            assertAll(
                    () -> assertThat(hostAndPort.getHost()).isEqualTo(host),
                    () -> assertThat(hostAndPort.getPort()).isEqualTo(port)
            );
        }
    }
}
