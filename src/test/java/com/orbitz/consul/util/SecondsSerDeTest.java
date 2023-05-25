package com.orbitz.consul.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

class SecondsSerDeTest {

    static class Item {
        @JsonSerialize(using = SecondsSerializer.class)
        @JsonDeserialize(using = SecondsDeserializer.class)
        private Long seconds;

        public Item() {}

        Item(Long seconds) {
            this.seconds = seconds;
        }

        Long getSeconds() {
            return seconds;
        }
    }

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeSeconds() throws JsonProcessingException {
        Long seconds = new Random().nextLong();
        String expected = String.format("\"%ss\"", seconds);
        String json = objectMapper.writeValueAsString(new Item(seconds));

        assertTrue(json.contains(expected));
    }

    @Test
    void shouldDeserializeSeconds() throws IOException {
        Long seconds = new Random().nextLong();
        Item item = objectMapper.readValue(String.format("{\"seconds\": \"%ds\"}", seconds), Item.class);

        assertEquals(seconds, item.getSeconds());
    }

    @Test
    void shouldDeserializeSeconds_noS() throws IOException {
        Long seconds = new Random().nextLong();
        Item item = objectMapper.readValue(String.format("{\"seconds\": \"%d\"}", seconds), Item.class);

        assertEquals(seconds, item.getSeconds());
    }
}