package org.kiwiproject.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.kiwiproject.consul.model.health.ServiceHealth;

import java.util.List;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableQueryResults.class)
@JsonDeserialize(as = ImmutableQueryResults.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class QueryResults {

    @JsonProperty("Service")
    public abstract String service();

    @JsonProperty("Nodes")
    public abstract List<ServiceHealth> nodes();
}
