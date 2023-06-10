package org.kiwiproject.consul.model.health;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.kiwiproject.consul.model.catalog.TaggedAddresses;

import java.util.Map;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableNode.class)
@JsonDeserialize(as = ImmutableNode.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Node {

    @JsonProperty("Node")
    public abstract String getNode();

    @JsonProperty("Address")
    public abstract String getAddress();
    
    @JsonProperty("Datacenter")
    public abstract Optional<String> getDatacenter();

    @JsonProperty("TaggedAddresses")
    public abstract Optional<TaggedAddresses> getTaggedAddresses();

    @JsonProperty("Meta")
    public abstract Optional<Map<String,String>> getNodeMeta();
}
