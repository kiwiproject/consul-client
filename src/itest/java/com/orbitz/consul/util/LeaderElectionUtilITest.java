package com.orbitz.consul.util;

import com.orbitz.consul.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeaderElectionUtilITest extends BaseIntegrationTest {

    @Test
    void testGetLeaderInfoForService() throws Exception {
        LeaderElectionUtil leutil = new LeaderElectionUtil(client);
        final String serviceName = "myservice100";
        final String serviceInfo = "serviceinfo";

        leutil.releaseLockForService(serviceName);
        assertFalse(leutil.getLeaderInfoForService(serviceName).isPresent());
        assertEquals(serviceInfo, leutil.electNewLeaderForService(serviceName, serviceInfo).get());
        assertTrue(leutil.releaseLockForService(serviceName));
    }
}