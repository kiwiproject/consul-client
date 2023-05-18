package com.orbitz.consul;

import static org.junit.Assert.assertFalse;

import com.orbitz.consul.model.operator.RaftConfiguration;
import com.orbitz.consul.model.operator.RaftServer;

import org.junit.Test;

import java.util.List;

public class OperatorITest extends BaseIntegrationTest {

    @Test
    public void shouldGetRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    @Test
    public void shouldGetRaftConfigurationForDatacenter() {
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
    public void shouldGetStaleRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    @Test
    public void shouldGetStaleRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }
}
