package org.kiwiproject.consul.model.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.kiwiproject.consul.model.health.Node;
import org.kiwiproject.consul.model.health.Service;

import java.util.Map;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableCatalogNode.class)
@JsonDeserialize(as = ImmutableCatalogNode.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class CatalogNode {

    @JsonProperty("Node")
    public abstract Node getNode();

    @JsonProperty("Services")
    public abstract Map<String, Service> getServices();

}
