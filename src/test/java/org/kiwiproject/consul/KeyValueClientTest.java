package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.monitoring.NoOpClientEventCallback;
import org.kiwiproject.consul.option.Options;
import retrofit2.Call;
import retrofit2.Response;

class KeyValueClientTest {

    private KeyValueClient keyValueClient;
    private KeyValueClient.Api api;

    @BeforeEach
    void setUp() {
        api = mock(KeyValueClient.Api.class);
        var networkTimeoutConfig = new Consul.NetworkTimeoutConfig.Builder().build();
        keyValueClient = new KeyValueClient(api, new ClientConfig(), new NoOpClientEventCallback(), networkTimeoutConfig);
    }

    @Nested
    class GetValue {

        @Test
        void shouldReturnEmptyOptional_WhenConsulException_HasStatusCode_404() {
            var consulException = new ConsulException(mockCall(), mockResponseWithStatus(404));
            when(api.getValue(anyString(), anyMap())).thenThrow(consulException);

            assertThat(keyValueClient.getValue("someKey", Options.BLANK_QUERY_OPTIONS)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = { 401, 403, 500, 502, 503 })
        void shouldRethrowConsulException_WhenItHasStatusCode_OtherThan404(int statusCode) {           
            var consulException = new ConsulException(mockCall(), mockResponseWithStatus(statusCode));
            when(api.getValue(anyString(), anyMap())).thenThrow(consulException);

            assertThatThrownBy(() -> keyValueClient.getValue("someKey", Options.BLANK_QUERY_OPTIONS))
                    .isSameAs(consulException);
        }
    }

    @Nested
    class GetConsulResponseWithValue {

        @Test
        void shouldReturnEmptyOptional_WhenConsulException_HasStatusCode_404() {
            var consulException = new ConsulException(mockCall(), mockResponseWithStatus(404));
            when(api.getValue(anyString(), anyMap())).thenThrow(consulException);

            assertThat(keyValueClient.getConsulResponseWithValue("someKey", Options.BLANK_QUERY_OPTIONS)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = { 401, 403, 500, 502, 503 })
        void shouldRethrowConsulException_WhenItHasStatusCode_OtherThan404(int statusCode) {
            var consulException = new ConsulException(mockCall(), mockResponseWithStatus(statusCode));
            when(api.getValue(anyString(), anyMap())).thenThrow(consulException);

            assertThatThrownBy(() -> keyValueClient.getConsulResponseWithValue("someKey", Options.BLANK_QUERY_OPTIONS))
                    .isSameAs(consulException);
        }
    }

    private static Call<String> mockCall() {
        var request = new Request.Builder().url("http://localhost:9200").build();
        Call<String> call = mock();
        when(call.request()).thenReturn(request);
        return call;
    }

    private static <T> Response<T> mockResponseWithStatus(int status) {
        Response<T> response = mock();
        when(response.code()).thenReturn(status);
        return response;
    }
}
