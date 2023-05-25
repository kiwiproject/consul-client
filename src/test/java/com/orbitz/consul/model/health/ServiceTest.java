package com.orbitz.consul.model.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServiceTest {

    @Test
    void testEquals(){
        Service one = tagged("a", "b");
        Service two = tagged("a", "b");
        assertEquals(one, two);
    }

    @Test
    void testNullTags(){
        Service sv = tagged();
        assertTrue(sv.getTags().isEmpty());
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