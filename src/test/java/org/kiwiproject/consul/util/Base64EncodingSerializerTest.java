package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.kv.ImmutableOperation;
import org.kiwiproject.consul.model.kv.Operation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class Base64EncodingSerializerTest {

    @Test
    void shouldSerializePresent() throws IOException {
        var value = RandomStringUtils.secure().nextAlphabetic(12);
        var operation = ImmutableOperation.builder()
                .verb("set")
                .value(value)
                .build();

        String json = Jackson.MAPPER.writeValueAsString(operation);
        Operation deserialized = Jackson.MAPPER.readValue(json, Operation.class);

        assertThat(deserialized.value()).contains(value);
    }

    @Test
    void shouldSerializeAsBase64() throws IOException {
        var value = RandomStringUtils.secure().nextAlphabetic(12);
        var operation = ImmutableOperation.builder()
                .verb("set")
                .value(value)
                .build();

        String json = Jackson.MAPPER.writeValueAsString(operation);
        var expectedEncoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

        assertThat(json).contains(expectedEncoded);
    }

    @Test
    void shouldSerializeEmptyAsNull() throws IOException {
        var operation = ImmutableOperation.builder()
                .verb("set")
                .build();

        String json = Jackson.MAPPER.writeValueAsString(operation);

        assertThat(json).contains("\"Value\":null");
    }
}
