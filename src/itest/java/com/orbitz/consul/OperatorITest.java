package com.orbitz.consul;

import com.orbitz.consul.model.operator.RaftConfiguration;
import com.orbitz.consul.model.operator.RaftServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OperatorITest extends BaseIntegrationTest {

    @Test
    void shouldGetRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    @Test
    void shouldGetRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    private String getFirstDatacenter() {
        List<String> datacenters = client.catalogClient().getDatacenters();
        assertFalse(datacenters.isEmpty());
        String datacenter = datacenters.get(0);
        return datacenter;
    }

    @Test
    void shouldGetStaleRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    @Test
    void shouldGetStaleRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }
}
