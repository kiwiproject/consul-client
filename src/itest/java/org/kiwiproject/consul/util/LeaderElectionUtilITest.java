package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.BaseIntegrationTest;

class LeaderElectionUtilITest extends BaseIntegrationTest {

    @Test
    void testGetLeaderInfoForService() {
        var leaderElection = new LeaderElectionUtil(client);
        var serviceName = "myservice100";
        var serviceInfo = "serviceinfo";

        leaderElection.releaseLockForService(serviceName);
        assertThat(leaderElection.getLeaderInfoForService(serviceName)).isEmpty();
        assertThat(leaderElection.electNewLeaderForService(serviceName, serviceInfo)).contains(serviceInfo);
        assertThat(leaderElection.releaseLockForService(serviceName)).isTrue();
    }
}
