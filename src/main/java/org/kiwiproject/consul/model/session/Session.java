package org.kiwiproject.consul.model.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;
import java.util.Optional;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableSession.class)
@JsonDeserialize(as = ImmutableSession.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Session {

    @JsonProperty("LockDelay")
    public abstract Optional<String> getLockDelay();

    @JsonProperty("Name")
    public abstract Optional<String> getName();

    @JsonProperty("Node")
    public abstract Optional<String> getNode();

    @JsonProperty("Checks")
    public abstract List<String> getChecks();

    @JsonProperty("Behavior")
    public abstract Optional<String> getBehavior();

    @JsonProperty("TTL")
    public abstract Optional<String> getTtl();
}
