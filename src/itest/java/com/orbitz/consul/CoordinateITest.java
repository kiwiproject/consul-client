package com.orbitz.consul;

import static java.util.Objects.isNull;
import static org.junit.Assert.assertFalse;

import com.orbitz.consul.model.coordinate.Coordinate;
import com.orbitz.consul.model.coordinate.Datacenter;

import org.junit.Test;

import java.util.List;

public class CoordinateITest extends BaseIntegrationTest {

    @Test
    public void shouldGetDatacenters() {
        List<Datacenter> datacenters = client.coordinateClient().getDatacenters();
        assertFalse(datacenters.isEmpty());
    }

    @Test
    public void shouldGetNodes() {
        List<Coordinate> nodes = client.coordinateClient().getNodes();
        assertFalse(isNull(nodes));
    }

    @Test
    public void shouldGetNodesForDatacenter() {
        String datacenter = client.coordinateClient().getDatacenters().get(0).getDatacenter();

        List<Coordinate> nodes = client.coordinateClient().getNodes(datacenter);
        assertFalse(isNull(nodes));
    }
}
