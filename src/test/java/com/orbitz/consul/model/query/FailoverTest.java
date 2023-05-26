package com.orbitz.consul.model.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;

class FailoverTest {

    @Test
    void creatingFailoverWithDatacentersIsValid() {
        ImmutableFailover failover = ImmutableFailover.builder()
                .datacenters(Lists.newArrayList("dc1", "dc2"))
                .build();

        assertThat(failover.datacenters()).contains(List.of("dc1", "dc2"));
    }

    @Test
    void creatingFailoverWithNearestIsValid() {
        ImmutableFailover failover = ImmutableFailover.builder()
                .nearestN(2)
                .build();

        assertThat(failover.getNearestN()).contains(2);
    }

    @Test
    void creatingFailoverWithNearestAndDatacentersIsValid() {
        ImmutableFailover failover = ImmutableFailover.builder()
                .datacenters(Lists.newArrayList("dc1", "dc2"))
                .nearestN(2)
                .build();

        assertThat(failover.datacenters()).contains(List.of("dc1", "dc2"));
        assertThat(failover.getNearestN()).contains(2);
    }
}
