package org.kiwiproject.consul.util.failover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import okhttp3.HttpUrl;
import okhttp3.Interceptor.Chain;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.util.failover.strategy.ConsulFailoverStrategy;
import org.slf4j.Logger;

import javax.net.ssl.SSLProtocolException;
import java.io.IOException;
import java.util.Optional;

class ConsulFailoverInterceptorTest {

    private ConsulFailoverInterceptor interceptor;
    private ConsulFailoverStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = mock(ConsulFailoverStrategy.class);
        interceptor = new ConsulFailoverInterceptor(strategy);
    }

    @Test
    void shouldHaveDefaultMaximumFailoverAttempts() {
        assertThat(interceptor.maxFailoverAttempts()).isEqualTo(10);
    }

    @Test
    void shouldChangeMaximumFailoverAttempts_AndReturnSameInstance() {
        var updatedInterceptor = interceptor.withMaxFailoverAttempts(5);

        assertThat(updatedInterceptor).isSameAs(interceptor);
        assertThat(updatedInterceptor.maxFailoverAttempts()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(ints = { -10, -1, 0 })
    void shouldRequirePositiveMaxFailoverAttempts(int maxFailoverAttempts) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> interceptor.withMaxFailoverAttempts(maxFailoverAttempts));
    }

    @Test
    void shouldThrowException_WhenRequestIsNotViable() {
        when(strategy.isRequestViable(any(Request.class))).thenReturn(false);

        var chain = mock(Chain.class, RETURNS_DEEP_STUBS);

        // noinspection resource
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() -> interceptor.intercept(chain));

        verify(strategy, only()).isRequestViable(any(Request.class));
    }

    @Test
    void shouldThrowException_WhenNextStageIsEmpty() {
        when(strategy.isRequestViable(any(Request.class))).thenReturn(true);
        when(strategy.computeNextStage(any(Request.class), isNull(Response.class)))
                .thenReturn(Optional.empty());

        var chain = mock(Chain.class, RETURNS_DEEP_STUBS);

        // noinspection resource
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() -> interceptor.intercept(chain));

        verify(strategy).isRequestViable(any(Request.class));
        verify(strategy).computeNextStage(any(Request.class), isNull(Response.class));
        verifyNoMoreInteractions(strategy);
    }

    @Test
    void shouldReturnResponse_WhenGetResponse_BeforeExceedingMaxFailoverAttempts() throws IOException {
        when(strategy.isRequestViable(any(Request.class))).thenReturn(true);

        var request = newMockRequest();
        when(strategy.computeNextStage(any(Request.class), isNull(Response.class)))
                .thenReturn(Optional.of(request));

        var response = mock(Response.class);

        // reduce the max failover attempts; simulate failing one less than the maximum before succeeding
        interceptor.withMaxFailoverAttempts(3);

        var chain = mock(Chain.class, RETURNS_DEEP_STUBS);
        when(chain.proceed(any(Request.class)))
                .thenThrow(new RuntimeException("request 1 to consul failed"))
                .thenThrow(new RuntimeException("request 2 to consul failed"))
                .thenReturn(response);

        var theResponse = interceptor.intercept(chain);

        assertThat(theResponse).isSameAs(response);

        //noinspection resource
        verify(chain, times(3)).proceed(any(Request.class));
    }

    @Test
    void shouldThrowException_WhenMaxFailoverAttemptsExceeded() throws IOException {
        when(strategy.isRequestViable(any(Request.class))).thenReturn(true);

        var request = newMockRequest();
        when(strategy.computeNextStage(any(Request.class), isNull(Response.class)))
                .thenReturn(Optional.of(request));

        var chain = mock(Chain.class, RETURNS_DEEP_STUBS);
        when(chain.proceed(any(Request.class))).thenThrow(new RuntimeException("request to consul failed"));

        //noinspection resource
        assertThatExceptionOfType(MaxFailoverAttemptsExceededException.class)
                .isThrownBy(() -> interceptor.intercept(chain))
                .withMessage("Reached max failover attempts (10). Giving up.")
                .havingCause()
                .withMessage("request to consul failed");

        //noinspection resource
        verify(chain, times(10)).proceed(any(Request.class));
    }

    @Test
    void shouldLogExceptionThrownOnRequest_WhenAtWarnLevel() {
        var logger = newLoggerWithDebugEnabled(false);
        var request = newMockRequest();
        var exception = newSslProtocolException();

        ConsulFailoverInterceptor.logExceptionThrownOnRequest(logger, exception, request);

        verify(logger).isDebugEnabled();
        verify(logger).warn("Got '{}:{}' when connecting to {} (enable DEBUG level to see stack trace)",
                exception.getClass().getName(),
                exception.getMessage(),
                nextConsulHttpUrl());
        verifyNoMoreInteractions(logger);
    }

    @Test
    void shouldLogExceptionThrownOnRequest_WhenAtDebugLevel() {
        var logger = newLoggerWithDebugEnabled(true);
        var request = newMockRequest();
        var exception = newSslProtocolException();

        ConsulFailoverInterceptor.logExceptionThrownOnRequest(logger, exception, request);

        verify(logger).isDebugEnabled();
        verify(logger).debug("Got error when connecting to {}", nextConsulHttpUrl(), exception);
        verifyNoMoreInteractions(logger);
    }

    private static Logger newLoggerWithDebugEnabled(boolean value) {
        var logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(value);
        return logger;
    }

    private static Request newMockRequest() {
        var request = mock(Request.class);
        when(request.url()).thenReturn(nextConsulHttpUrl());
        return request;
    }

    private static SSLProtocolException newSslProtocolException() {
        return new SSLProtocolException("The certificate chain length (11) exceeds the maximum allowed length (10)");
    }

    private static HttpUrl nextConsulHttpUrl() {
        return HttpUrl.parse("https://consul.acme.com:8501");
    }
}
