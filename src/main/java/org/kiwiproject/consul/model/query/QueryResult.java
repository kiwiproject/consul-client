package org.kiwiproject.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;
import org.kiwiproject.consul.model.health.HealthCheck;
import org.kiwiproject.consul.model.health.Node;
import org.kiwiproject.consul.model.health.Service;

import java.util.List;
import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableQueryResult.class)
@JsonDeserialize(as = ImmutableQueryResult.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class QueryResult {

    @JsonProperty("Node")
    public abstract Node getNode();

    @JsonProperty("Service")
    public abstract Service getService();

    @JsonProperty("Checks")
    @JsonDeserialize(as = ImmutableList.class, contentAs = HealthCheck.class)
    public abstract List<HealthCheck> getChecks();
    @JsonProperty("DNS")
    public abstract Optional<DnsQuery> getDns();

    @JsonProperty("Datacenters")
    public abstract Optional<String> datacenters();

    @JsonProperty("Failovers")
    public abstract Optional<Integer> failovers();
}
