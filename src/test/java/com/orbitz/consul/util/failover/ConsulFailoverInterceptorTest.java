package com.orbitz.consul.util.failover;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.orbitz.consul.ConsulException;
import com.orbitz.consul.util.failover.strategy.ConsulFailoverStrategy;
import okhttp3.Interceptor.Chain;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
