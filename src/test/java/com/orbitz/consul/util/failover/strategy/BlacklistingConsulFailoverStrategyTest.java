package com.orbitz.consul.util.failover.strategy;

import static org.assertj.core.api.Assertions.assertThat;
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
        final Collection<HostAndPort> targets = new ArrayList<>();
        targets.add(HostAndPort.fromParts("1.2.3.4", 8501));
        targets.add(HostAndPort.fromParts("localhost", 8501));

        blacklistingConsulFailoverStrategy = new BlacklistingConsulFailoverStrategy(targets, 100000);
    }

    @Test
    void getFirstUrlBack() {
        Request previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
        Response previousResponse = null;

        Optional<Request> result = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

        assertThat(result.isPresent()).isEqualTo(true);
        assertThat(result.get().url().toString()).isEqualTo("https://1.2.3.4:8501/v1/agent/members");
    }

    @Test
    void getSecondUrlBackAfterFirstOneIsBlacklisted() {
        Request previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
        Response previousResponse = null;

        Optional<Request> result1 = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

        assertThat(result1.isPresent()).isEqualTo(true);
        assertThat(result1.get().url().toString()).isEqualTo("https://1.2.3.4:8501/v1/agent/members");

        blacklistingConsulFailoverStrategy.markRequestFailed(result1.get());
        Optional<Request> result2 = blacklistingConsulFailoverStrategy.computeNextStage(result1.get(), previousResponse);

        assertThat(result2.isPresent()).isEqualTo(true);
        assertThat(result2.get().url().toString()).isEqualTo("https://localhost:8501/v1/agent/members");
    }

    @Test
    void getNoUrlBackAfterBothAreBlacklisted() {
        Request previousRequest = new Request.Builder().url("https://1.2.3.4:8501/v1/agent/members").build();
        Response previousResponse = null;

        Optional<Request> result1 = blacklistingConsulFailoverStrategy.computeNextStage(previousRequest, previousResponse);

        assertThat(result1.isPresent()).isEqualTo(true);
        assertThat(result1.get().url().toString()).isEqualTo("https://1.2.3.4:8501/v1/agent/members");

        blacklistingConsulFailoverStrategy.markRequestFailed(result1.get());
        Optional<Request> result2 = blacklistingConsulFailoverStrategy.computeNextStage(result1.get(), previousResponse);

        assertThat(result2.isPresent()).isEqualTo(true);
        assertThat(result2.get().url().toString()).isEqualTo("https://localhost:8501/v1/agent/members");

        blacklistingConsulFailoverStrategy.markRequestFailed(result2.get());

        Optional<Request> result3 = blacklistingConsulFailoverStrategy.computeNextStage(result2.get(), previousResponse);

        assertThat(result3.isPresent()).isEqualTo(false);
    }
}
