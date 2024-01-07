package org.kiwiproject.consul.model.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceTest {

    @Test
    void testEquals() {
        var service1 = newTaggedService("a", "b");
        var service2 = newTaggedService("a", "b");
        assertThat(service2).isEqualTo(service1);
    }

    @Test
    void testNullTags() {
        var service = newTaggedService();
        assertThat(service.getTags()).isEmpty();
    }

    private Service newTaggedService(String ... tags) {
        return ImmutableService
                .builder()
                .address("localhost")
                .service("service")
                .id("id")
                .addTags(tags)
                .port(4333)
                .build();
    }

}
