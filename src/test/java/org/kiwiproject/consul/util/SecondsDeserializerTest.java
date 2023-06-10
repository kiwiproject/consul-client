package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

class SecondsDeserializerTest {

    private SecondsDeserializer deserializer;

    private JsonParser jsonParser;

    @BeforeEach
    void setUp() {
        deserializer = new SecondsDeserializer();
        jsonParser = mock(JsonParser.class);
    }

    @Test
    void shouldReturnNull_WhenGivenNullString() throws IOException {
        mockJsonParserToReturnValue(null);

        assertThat(deserializer.deserialize(jsonParser, null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\r\n", "\t\t\t" })
    void shouldReturnNull_WhenGivenBlankString(String consulDuration) throws IOException {
        mockJsonParserToReturnValue(consulDuration);

        assertThat(deserializer.deserialize(jsonParser, null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "10s, 10",
            "5s, 5",
            "42s, 42",

            // the following are not expected from Consul, but should still work
            "15, 15",
            "z10s, 10",
            "27ms, 27",
            "57seconds, 57",
            "2minutes, 2"
    })
    void shouldDeserializeConsulDurationToNumericValue(String consulDuration, Long expectedValue) throws IOException {
        mockJsonParserToReturnValue(consulDuration);

        var deserializedValue = deserializer.deserialize(jsonParser, null);
        assertThat(deserializedValue).isEqualTo(expectedValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "foo",
            "10 s",
            "5 minutes"
    })
    void shouldThrowIllegalStateException_WhenGivenInvalidConsulDuration(String consulDuration) throws IOException {
        mockJsonParserToReturnValue(consulDuration);

        assertThatIllegalStateException().isThrownBy(() -> deserializer.deserialize(jsonParser, null))
                .withCauseExactlyInstanceOf(NumberFormatException.class)
                .withMessage("Expected a number but received a non-numeric value");
    }

    private void mockJsonParserToReturnValue(String consulDuration) throws IOException {
        when(jsonParser.getValueAsString()).thenReturn(consulDuration);
    }
}
