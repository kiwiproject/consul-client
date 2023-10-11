package org.kiwiproject.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonDeserialize(as = ImmutableTemplate.class)
@JsonSerialize(as = ImmutableTemplate.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Template {

    @JsonProperty("Type")
    public abstract String getType();

    @JsonProperty("RegExp")
    public abstract Optional<String> getRegExp();
}
