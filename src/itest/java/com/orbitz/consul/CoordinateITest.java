package com.orbitz.consul;

import static java.util.Objects.isNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.orbitz.consul.model.coordinate.Coordinate;
import com.orbitz.consul.model.coordinate.Datacenter;
import org.junit.jupiter.api.Test;

import java.util.List;

class CoordinateITest extends BaseIntegrationTest {

    @Test
    void shouldGetDatacenters() {
        List<Datacenter> datacenters = client.coordinateClient().getDatacenters();
        assertFalse(datacenters.isEmpty());
    }

    @Test
    void shouldGetNodes() {
        List<Coordinate> nodes = client.coordinateClient().getNodes();
        assertFalse(isNull(nodes));
    }

    @Test
    void shouldGetNodesForDatacenter() {
        String datacenter = client.coordinateClient().getDatacenters().get(0).getDatacenter();

        List<Coordinate> nodes = client.coordinateClient().getNodes(datacenter);
        assertFalse(isNull(nodes));
    }
}
