package com.orbitz.consul.cache;

import static com.orbitz.consul.Awaiting.awaitAtMost500ms;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.model.State;
import com.orbitz.consul.model.health.HealthCheck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class HealthCheckCacheITest extends BaseIntegrationTest {

    private AgentClient agentClient;

    @BeforeEach
    void setUp() {
        agentClient = client.agentClient();
    }

    @Test
    void cacheShouldContainPassingTestsOnly() throws Exception {
        var healthClient = client.healthClient();
        var checkName = randomUUIDString();
        var checkId = randomUUIDString();

        agentClient.registerCheck(checkId, checkName, 20L);
        try {
            agentClient.passCheck(checkId);
            awaitAtMost500ms().until(() -> checkIsPassing(checkId));

            try (HealthCheckCache hCheck = HealthCheckCache.newCache(healthClient, State.PASS)) {
                hCheck.start();
                hCheck.awaitInitialized(3, TimeUnit.SECONDS);

                HealthCheck check = hCheck.getMap().get(checkId);
                assertThat(check.getCheckId()).isEqualTo(checkId);

                agentClient.failCheck(checkId);

                awaitAtMost500ms().until(() -> isNull(hCheck.getMap().get(checkId)));
            }
        }
        finally {
            agentClient.deregisterCheck(checkId);
        }
    }

    private boolean checkIsPassing(String checkId) {
        return agentClient.getChecks()
            .values()
            .stream()
            .anyMatch(check ->
                check.getCheckId().equals(checkId) && State.fromName(check.getStatus()) == State.PASS);
    }
}
