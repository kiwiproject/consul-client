package com.orbitz.consul;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbitz.consul.model.operator.RaftConfiguration;
import com.orbitz.consul.model.operator.RaftServer;
import org.junit.jupiter.api.Test;

import java.util.List;

class OperatorITest extends BaseIntegrationTest {

    @Test
    void shouldGetRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers).isNotEmpty();
    }

    @Test
    void shouldGetRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers).isNotEmpty();
    }

    private String getFirstDatacenter() {
        List<String> datacenters = client.catalogClient().getDatacenters();
        assertThat(datacenters).isNotEmpty();
        return datacenters.get(0);
    }

    @Test
    void shouldGetStaleRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers).isNotEmpty();
    }

    @Test
    void shouldGetStaleRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers).isNotEmpty();
    }
}
