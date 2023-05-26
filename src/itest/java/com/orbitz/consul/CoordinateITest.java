package com.orbitz.consul;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.orbitz.consul.model.coordinate.Coordinate;
import com.orbitz.consul.model.coordinate.Datacenter;
import org.junit.jupiter.api.Test;

import java.util.List;

class CoordinateITest extends BaseIntegrationTest {

    @Test
    void shouldGetDatacenters() {
        List<Datacenter> datacenters = client.coordinateClient().getDatacenters();
        assertThat(datacenters).isNotEmpty();
    }

    @Test
    void shouldGetNodes() {
        List<Coordinate> nodes = client.coordinateClient().getNodes();
        assertThat(isNull(nodes)).isFalse();
    }

    @Test
    void shouldGetNodesForDatacenter() {
        String datacenter = client.coordinateClient().getDatacenters().get(0).getDatacenter();

        List<Coordinate> nodes = client.coordinateClient().getNodes(datacenter);
        assertThat(isNull(nodes)).isFalse();
    }
}
