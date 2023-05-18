package com.orbitz.consul.model.catalog;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import java.util.Optional;

public class TaggedAddressesTest {

    @Test
    public void buildingTaggedAddressWithAllAttributesShouldSucceed() {
        ImmutableTaggedAddresses taggedAddresses = ImmutableTaggedAddresses.builder()
                .lan("127.0.0.1")
                .wan("172.217.17.110")
                .build();

        assertThat(taggedAddresses.getLan(), is(Optional.of("127.0.0.1")));
        assertThat(taggedAddresses.getWan(), is("172.217.17.110"));
    }

    @Test
    public void buildingTaggedAddressWithoutLanAddressShouldSucceed() {
        ImmutableTaggedAddresses taggedAddresses = ImmutableTaggedAddresses.builder()
                .wan("172.217.17.110")
                .build();

        assertThat(taggedAddresses.getLan(), is(Optional.empty()));
        assertThat(taggedAddresses.getWan(), is("172.217.17.110"));
    }

    @Test(expected = IllegalStateException.class)
    public void buildingTaggedAddressWithoutWanAddressShouldThrow() {
        ImmutableTaggedAddresses.builder()
                .lan("127.0.0.1")
                .build();
    }

}
