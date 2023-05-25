package com.orbitz.consul.model.query;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.Lists;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class FailoverTest {

    @Test
    void creatingFailoverWithDatacentersIsValid() {
        ImmutableFailover failover = ImmutableFailover.builder()
                .datacenters(Lists.newArrayList("dc1", "dc2"))
                .build();

        assertThat(failover.datacenters(), is(Optional.of(Lists.newArrayList("dc1", "dc2"))));
    }

    @Test
    void creatingFailoverWithNearestIsValid() {
        ImmutableFailover failover = ImmutableFailover.builder()
                .nearestN(2)
                .build();

        assertThat(failover.getNearestN(), is(Optional.of(2)));
    }

    @Test
    void creatingFailoverWithNearestAndDatacentersIsValid() {
        ImmutableFailover failover = ImmutableFailover.builder()
                .datacenters(Lists.newArrayList("dc1", "dc2"))
                .nearestN(2)
                .build();

        assertThat(failover.datacenters(), is(Optional.of(Lists.newArrayList("dc1", "dc2"))));
        assertThat(failover.getNearestN(), is(Optional.of(2)));
    }
}
