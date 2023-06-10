package org.kiwiproject.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value.Immutable;

@Immutable
@JsonDeserialize(as = ImmutableDnsQuery.class)
@JsonSerialize(as = ImmutableDnsQuery.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DnsQuery {

    @JsonProperty("TTL")
    public abstract String getTtl();
}
