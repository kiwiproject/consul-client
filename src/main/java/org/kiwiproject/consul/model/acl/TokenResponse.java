package org.kiwiproject.consul.model.acl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableTokenResponse.class)
@JsonDeserialize(as = ImmutableTokenResponse.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TokenResponse extends BaseTokenResponse {
    @JsonProperty("SecretID")
    public abstract String secretId();
}
