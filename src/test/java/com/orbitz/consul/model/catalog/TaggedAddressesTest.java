package com.orbitz.consul.model.catalog;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TaggedAddressesTest {

    @Test
    void buildingTaggedAddressWithAllAttributesShouldSucceed() {
        ImmutableTaggedAddresses taggedAddresses = ImmutableTaggedAddresses.builder()
                .lan("127.0.0.1")
                .wan("172.217.17.110")
                .build();

        assertThat(taggedAddresses.getLan(), is(Optional.of("127.0.0.1")));
        assertThat(taggedAddresses.getWan(), is("172.217.17.110"));
    }

    @Test
    void buildingTaggedAddressWithoutLanAddressShouldSucceed() {
        ImmutableTaggedAddresses taggedAddresses = ImmutableTaggedAddresses.builder()
                .wan("172.217.17.110")
                .build();

        assertThat(taggedAddresses.getLan(), is(Optional.empty()));
        assertThat(taggedAddresses.getWan(), is("172.217.17.110"));
    }

    @Test
    void buildingTaggedAddressWithoutWanAddressShouldThrow() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            ImmutableTaggedAddresses.builder()
                    .lan("127.0.0.1")
                    .build();
        });
    }

}
