package com.orbitz.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value.Immutable;

@Immutable
@JsonDeserialize(as = ImmutableQueryId.class)
@JsonSerialize(as = ImmutableQueryId.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class QueryId {

    @JsonProperty("ID")
    public abstract String getId();
}
