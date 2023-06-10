package org.kiwiproject.consul.model.kv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.kiwiproject.consul.util.Base64EncodingDeserializer;
import org.kiwiproject.consul.util.Base64EncodingSerializer;

import java.math.BigInteger;
import java.util.Optional;

@Value.Immutable
@JsonDeserialize(as = ImmutableOperation.class)
@JsonSerialize(as = ImmutableOperation.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Operation {

    @JsonProperty("Verb")
    public abstract String verb();

    @JsonProperty("Key")
    public abstract Optional<String> key();

    @JsonProperty("Value")
    @JsonSerialize(using = Base64EncodingSerializer.class)
    @JsonDeserialize(using = Base64EncodingDeserializer.class)
    public abstract Optional<String> value();

    @JsonProperty("Flags")
    public abstract Optional<Long> flags();

    @JsonProperty("Index")
    public abstract Optional<BigInteger> index();

    @JsonProperty("Session")
    public abstract Optional<String> session();

    public static ImmutableOperation.Builder builder(Verb verb) {
        return ImmutableOperation.builder().verb(verb.toValue());
    }
}
