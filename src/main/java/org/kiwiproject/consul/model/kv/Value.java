package org.kiwiproject.consul.model.kv;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import org.kiwiproject.consul.util.UnsignedLongDeserializer;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Optional;

@Immutable
@JsonDeserialize(as = ImmutableValue.class)
@JsonSerialize(as = ImmutableValue.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Value {

    @JsonProperty("CreateIndex")
    public abstract long getCreateIndex();

    @JsonProperty("ModifyIndex")
    public abstract long getModifyIndex();

    @JsonProperty("LockIndex")
    public abstract long getLockIndex();

    @JsonProperty("Key")
    public abstract String getKey();

    @JsonProperty("Flags")
    @JsonDeserialize(using = UnsignedLongDeserializer.class)
    public abstract long getFlags();

    @JsonProperty("Value")
    public abstract Optional<String> getValue();

    @JsonProperty("Session")
    public abstract Optional<String> getSession();

    @JsonIgnore
    @Lazy
    public Optional<String> getValueAsString() {
        return getValueAsString(Charset.defaultCharset());
    }

    @JsonIgnore
    @Lazy
    public Optional<String> getValueAsString(Charset charset) {
        return getValue().map(s -> new String(Base64.getDecoder().decode(s), charset));
    }

    @JsonIgnore
    @Lazy
    public Optional<byte[]> getValueAsBytes() {
        return getValue().map(s -> Base64.getDecoder().decode(s));
    }
}
