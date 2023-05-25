package com.orbitz.consul.cache;

import static com.orbitz.consul.Awaiting.awaitWith25MsPoll;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.Objects.isNull;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.junit.Assert.assertEquals;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.State;
import com.orbitz.consul.model.health.HealthCheck;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class HealthCheckCacheITest extends BaseIntegrationTest {

    private AgentClient agentClient;

    @Before
    public void setUp() {
        agentClient = client.agentClient();
    }

    @Test
    public void cacheShouldContainPassingTestsOnly() throws Exception {
        HealthClient healthClient = client.healthClient();
        String checkName = randomUUIDString();
        String checkId = randomUUIDString();

        agentClient.registerCheck(checkId, checkName, 20L);
        try {
            agentClient.passCheck(checkId);
            awaitWith25MsPoll().atMost(ONE_HUNDRED_MILLISECONDS).until(() -> checkIsPassing(checkId));

            try (HealthCheckCache hCheck = HealthCheckCache.newCache(healthClient, State.PASS)) {
                hCheck.start();
                hCheck.awaitInitialized(3, TimeUnit.SECONDS);

                HealthCheck check = hCheck.getMap().get(checkId);
                assertEquals(checkId, check.getCheckId());

                agentClient.failCheck(checkId);

                awaitWith25MsPoll().atMost(ONE_HUNDRED_MILLISECONDS)
                        .until(() -> isNull(hCheck.getMap().get(checkId)));
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
