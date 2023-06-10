package org.kiwiproject.consul.util.failover.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HostAndPort;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

class BlacklistingConsulFailoverStrategyTest {

    private BlacklistingConsulFailoverStrategy blacklistingConsulFailoverStrategy;

    @BeforeEach
    void setup() {
        // Create a set of targets
        Collection<HostAndPort> targets = new ArrayList<>();
        targets.add(HostAndPort.fromParts("1.2.3.4", 8501));
        targets.add(HostAndPort.fromParts("localhost", 8501));

        blacklistingConsulFailoverStrategy = new BlacklistingConsulFailoverStrategy(targets, 100_000);
    }

    @Test
    void getFirstUrlBack() {
        var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
        Response previousResponse = null;

        // noinspection ConstantValue
        Optional<Request> result = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().url()).hasToString("https://1.2.3.4:8501/v1/agent/members");
    }

    @Test
    void getSecondUrlBackAfterFirstOneIsBlacklisted() {
        var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
        Response previousResponse = null;

        // noinspection ConstantValue
        Optional<Request> result1 = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

        assertThat(result1).isPresent();
        assertThat(result1.orElseThrow().url()).hasToString("https://1.2.3.4:8501/v1/agent/members");

        blacklistingConsulFailoverStrategy.markRequestFailed(result1.get());
        // noinspection ConstantValue
        Optional<Request> result2 = blacklistingConsulFailoverStrategy.computeNextStage(result1.get(), previousResponse);

        assertThat(result2).isPresent();
        assertThat(result2.orElseThrow().url()).hasToString("https://localhost:8501/v1/agent/members");
    }

    @Test
    void getNoUrlBackAfterBothAreBlacklisted() {
        var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
        Response previousResponse = null;

        // noinspection ConstantValue
        Optional<Request> result1 = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

        assertThat(result1).isPresent();
        assertThat(result1.orElseThrow().url()).hasToString("https://1.2.3.4:8501/v1/agent/members");

        blacklistingConsulFailoverStrategy.markRequestFailed(result1.get());
        // noinspection ConstantValue
        Optional<Request> result2 = blacklistingConsulFailoverStrategy.computeNextStage(result1.get(), previousResponse);

        assertThat(result2).isPresent();
        assertThat(result2.orElseThrow().url()).hasToString("https://localhost:8501/v1/agent/members");

        blacklistingConsulFailoverStrategy.markRequestFailed(result2.get());

        // noinspection ConstantValue
        Optional<Request> result3 = blacklistingConsulFailoverStrategy.computeNextStage(result2.get(), previousResponse);

        assertThat(result3).isEmpty();
    }

    @Test
    void shouldGetPreviousRequestUrl_WhenPreviousResponseSucceeded() {
        var previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();

        var previousResponse = mock(Response.class);
        when(previousResponse.isSuccessful()).thenReturn(true);

        Optional<Request> nextRequestOptional = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

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

        Optional<Request> nextRequestOptional = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

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

        Optional<Request> nextRequestOptional = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

        assertThat(nextRequestOptional).isPresent();

        var nextRequest = nextRequestOptional.orElseThrow();
        assertThat(nextRequest.url()).hasToString("https://localhost:8501/v1/agent/members");
    }
}
