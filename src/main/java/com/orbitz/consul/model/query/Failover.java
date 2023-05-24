package com.orbitz.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value.Immutable;

import java.util.List;
import java.util.Optional;

@Immutable
@JsonDeserialize(as = ImmutableFailover.class)
@JsonSerialize(as = ImmutableFailover.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Failover {

    @JsonProperty("NearestN")
    public abstract Optional<Integer> getNearestN();

    @JsonProperty("Datacenters")
    public abstract Optional<List<String>> datacenters();
}
