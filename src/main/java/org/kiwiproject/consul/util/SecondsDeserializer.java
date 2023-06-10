package org.kiwiproject.consul.util;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Deserializes Consul time values with "s" suffix to {@link Long} objects.
 *
 * @see <a href="https://developer.hashicorp.com/consul/api-docs/features/blocking">Blocking Queries</a>
 */
public class SecondsDeserializer extends JsonDeserializer<Long> {

    private static final Pattern PATTERN = Pattern.compile("[a-zA-Z]");

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var durationString = p.getValueAsString();

        if (isNotBlank(durationString)) {
            var duration = PATTERN.matcher(durationString).replaceAll("");
            return toLongOrThrow(duration);
        }

        return null;
    }

    private static Long toLongOrThrow(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Expected a number but received a non-numeric value", e);
        }
    }
}
