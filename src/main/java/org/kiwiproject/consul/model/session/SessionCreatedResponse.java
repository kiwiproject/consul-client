package org.kiwiproject.consul.model.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSessionCreatedResponse.class)
@JsonDeserialize(as = ImmutableSessionCreatedResponse.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SessionCreatedResponse {

    @JsonProperty("ID")
    public abstract String getId();
}
