package org.kiwiproject.consul.model.operator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.math.BigInteger;
import java.util.List;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonDeserialize(as = ImmutableRaftConfiguration.class)
@JsonSerialize(as = ImmutableRaftConfiguration.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class RaftConfiguration {

    @JsonProperty("Servers")
    public abstract List<RaftServer> servers();

    @JsonProperty("Index")
    public abstract BigInteger index();
}
