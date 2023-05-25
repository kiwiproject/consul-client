package com.orbitz.consul;

import com.orbitz.consul.model.operator.RaftConfiguration;
import com.orbitz.consul.model.operator.RaftServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorITest extends BaseIntegrationTest {

    @Test
    void shouldGetRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers.isEmpty()).isFalse();
    }

    @Test
    void shouldGetRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers.isEmpty()).isFalse();
    }

    private String getFirstDatacenter() {
        List<String> datacenters = client.catalogClient().getDatacenters();
        assertThat(datacenters.isEmpty()).isFalse();
        String datacenter = datacenters.get(0);
        return datacenter;
    }

    @Test
    void shouldGetStaleRaftConfiguration() {
        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers.isEmpty()).isFalse();
    }

    @Test
    void shouldGetStaleRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        OperatorClient operatorClient = client.operatorClient();
        RaftConfiguration raftConfiguration = operatorClient.getStaleRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers.isEmpty()).isFalse();
    }
}
