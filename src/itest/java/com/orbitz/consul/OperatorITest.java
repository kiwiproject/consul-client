package com.orbitz.consul;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbitz.consul.model.operator.RaftServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class OperatorITest extends BaseIntegrationTest {

    private OperatorClient operatorClient;

    @BeforeEach
    void setUp() {
        operatorClient = client.operatorClient();
    }

    @Test
    void shouldGetRaftConfiguration() {
        var raftConfiguration = operatorClient.getRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers).isNotEmpty();
    }

    @Test
    void shouldGetRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        var raftConfiguration = operatorClient.getRaftConfiguration(datacenter);

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
        var raftConfiguration = operatorClient.getStaleRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers).isNotEmpty();
    }

    @Test
    void shouldGetStaleRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        var raftConfiguration = operatorClient.getStaleRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertThat(servers).isNotEmpty();
    }
}
