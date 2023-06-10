package org.kiwiproject.consul.cache;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.consul.Awaiting.awaitAtMost500ms;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.BaseIntegrationTest;
import org.kiwiproject.consul.model.State;
import org.kiwiproject.consul.model.health.HealthCheck;

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
                assertThat(check).isNotNull();
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
