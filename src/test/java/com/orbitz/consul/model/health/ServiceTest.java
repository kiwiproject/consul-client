package com.orbitz.consul.model.health;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServiceTest {

    @Test
    public void testEquals(){
        Service one = tagged("a", "b");
        Service two = tagged("a", "b");
        assertEquals(one, two);
    }

    @Test
    public void testNullTags(){
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