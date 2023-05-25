package com.orbitz.consul.util;

import com.orbitz.consul.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderElectionUtilITest extends BaseIntegrationTest {

    @Test
    void testGetLeaderInfoForService() throws Exception {
        LeaderElectionUtil leutil = new LeaderElectionUtil(client);
        final String serviceName = "myservice100";
        final String serviceInfo = "serviceinfo";

        leutil.releaseLockForService(serviceName);
        assertThat(leutil.getLeaderInfoForService(serviceName).isPresent()).isFalse();
        assertThat(leutil.electNewLeaderForService(serviceName, serviceInfo).get()).isEqualTo(serviceInfo);
        assertThat(leutil.releaseLockForService(serviceName)).isTrue();
    }
}