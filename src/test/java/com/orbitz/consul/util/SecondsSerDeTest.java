package com.orbitz.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldSerializeSeconds() throws JsonProcessingException {
        Long seconds = new Random().nextLong();
        String expected = String.format("\"%ss\"", seconds);
        String json = OBJECT_MAPPER.writeValueAsString(new Item(seconds));

        assertThat(json).contains(expected);
    }

    @Test
    void shouldDeserializeSeconds() throws IOException {
        Long seconds = new Random().nextLong();
        Item item = OBJECT_MAPPER.readValue(String.format("{\"seconds\": \"%ds\"}", seconds), Item.class);

        assertThat(item.getSeconds()).isEqualTo(seconds);
    }

    @Test
    void shouldDeserializeSeconds_noS() throws IOException {
        Long seconds = new Random().nextLong();
        Item item = OBJECT_MAPPER.readValue(String.format("{\"seconds\": \"%d\"}", seconds), Item.class);

        assertThat(item.getSeconds()).isEqualTo(seconds);
    }
}
