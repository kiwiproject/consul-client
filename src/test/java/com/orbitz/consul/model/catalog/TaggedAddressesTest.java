package com.orbitz.consul.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class TaggedAddressesTest {

    @Test
    void buildingTaggedAddressWithAllAttributesShouldSucceed() {
        ImmutableTaggedAddresses taggedAddresses = ImmutableTaggedAddresses.builder()
                .lan("127.0.0.1")
                .wan("172.217.17.110")
                .build();

        assertThat(taggedAddresses.getLan()).contains("127.0.0.1");
        assertThat(taggedAddresses.getWan()).isEqualTo("172.217.17.110");
    }

    @Test
    void buildingTaggedAddressWithoutLanAddressShouldSucceed() {
        ImmutableTaggedAddresses taggedAddresses = ImmutableTaggedAddresses.builder()
                .wan("172.217.17.110")
                .build();

        assertThat(taggedAddresses.getLan()).isEmpty();
        assertThat(taggedAddresses.getWan()).isEqualTo("172.217.17.110");
    }

    @Test
    void buildingTaggedAddressWithoutWanAddressShouldThrow() {
        // noinspection ResultOfMethodCallIgnored
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                ImmutableTaggedAddresses.builder()
                        .lan("127.0.0.1")
                        .build());
    }

}
