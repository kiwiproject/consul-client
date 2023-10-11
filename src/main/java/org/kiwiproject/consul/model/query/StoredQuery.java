package org.kiwiproject.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonDeserialize(as = ImmutableStoredQuery.class)
@JsonSerialize(as = ImmutableStoredQuery.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class StoredQuery {

    @JsonProperty("ID")
    public abstract String getId();

    @JsonProperty("Name")
    public abstract String getName();

    @JsonProperty("Session")
    public abstract String getSession();

    @JsonProperty("Token")
    public abstract String getToken();

    @JsonProperty("Service")
    public abstract ServiceQuery getService();

    @JsonProperty("DNS")
    public abstract DnsQuery getDns();
}
