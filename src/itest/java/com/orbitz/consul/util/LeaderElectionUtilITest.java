package com.orbitz.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbitz.consul.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class LeaderElectionUtilITest extends BaseIntegrationTest {

    @Test
    void testGetLeaderInfoForService() {
        LeaderElectionUtil leutil = new LeaderElectionUtil(client);
        final String serviceName = "myservice100";
        final String serviceInfo = "serviceinfo";

        leutil.releaseLockForService(serviceName);
        assertThat(leutil.getLeaderInfoForService(serviceName)).isEmpty();
        assertThat(leutil.electNewLeaderForService(serviceName, serviceInfo)).contains(serviceInfo);
        assertThat(leutil.releaseLockForService(serviceName)).isTrue();
    }
}
