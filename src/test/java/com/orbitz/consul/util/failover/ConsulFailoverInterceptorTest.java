package com.orbitz.consul.util.failover;

import static org.junit.Assert.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.orbitz.consul.ConsulException;
import com.orbitz.consul.util.failover.strategy.ConsulFailoverStrategy;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Interceptor.Chain;

public class ConsulFailoverInterceptorTest {

    private ConsulFailoverInterceptor interceptor;
    private ConsulFailoverStrategy strategy;

    @Before
    public void setUp() {
        strategy = mock(ConsulFailoverStrategy.class);
        interceptor = new ConsulFailoverInterceptor(strategy);
    }

    @Test
    public void shouldThrowException_WhenRequestIsNotViable() {
        when(strategy.isRequestViable(any(Request.class))).thenReturn(false);

        var chain = mock(Chain.class, RETURNS_DEEP_STUBS);

        assertThrows(ConsulException.class, () -> interceptor.intercept(chain));

        verify(strategy, only()).isRequestViable(any(Request.class));
    }

    @Test
    public void shouldThrowException_WhenNextStageIsEmpty() {
        when(strategy.isRequestViable(any(Request.class))).thenReturn(true);
        when(strategy.computeNextStage(any(Request.class), isNull(Response.class)))
                .thenReturn(Optional.empty());

        var chain = mock(Chain.class, RETURNS_DEEP_STUBS);

        assertThrows(ConsulException.class, () -> interceptor.intercept(chain));

        verify(strategy).isRequestViable(any(Request.class));
        verify(strategy).computeNextStage(any(Request.class), isNull(Response.class));
        verifyNoMoreInteractions(strategy);
    }
}
