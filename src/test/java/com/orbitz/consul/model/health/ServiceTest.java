package com.orbitz.consul.model.health;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ServiceTest {

    @Test
    void testEquals(){
        Service one = tagged("a", "b");
        Service two = tagged("a", "b");
        assertThat(two).isEqualTo(one);
    }

    @Test
    void testNullTags(){
        Service sv = tagged();
        assertThat(sv.getTags().isEmpty()).isTrue();
    }

    private Service tagged(String ... tags) {
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