package org.kiwiproject.consul.model.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableAgent.class)
@JsonDeserialize(as = ImmutableAgent.class)
public abstract class Agent {

    @JsonProperty("Config")
    public abstract Config getConfig();

    @Nullable
    @JsonProperty("DebugConfig")
    public abstract DebugConfig getDebugConfig();

    @JsonProperty("Member")
    public abstract Member getMember();

    @JsonProperty("Meta")
    public abstract Map<String, String> getMeta();
}
