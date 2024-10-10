package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.coordinate.Coordinate;
import org.kiwiproject.consul.model.coordinate.Datacenter;

import java.util.List;

class CoordinateClientITest extends BaseIntegrationTest {

    @Test
    void shouldGetDatacenters() {
        List<Datacenter> datacenters = client.coordinateClient().getDatacenters();
        assertThat(datacenters).isNotEmpty();
    }

    @Test
    void shouldGetNodes() {
        List<Coordinate> nodes = client.coordinateClient().getNodes();
        assertThat(nodes).isNotNull();
    }

    @Test
    void shouldGetNodesForDatacenter() {
        String datacenter = client.coordinateClient().getDatacenters().get(0).getDatacenter();

        List<Coordinate> nodes = client.coordinateClient().getNodes(datacenter);
        assertThat(nodes).isNotNull();
    }
}
