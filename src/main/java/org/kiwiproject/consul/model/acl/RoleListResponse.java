package org.kiwiproject.consul.model.acl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableRoleListResponse.class)
@JsonDeserialize(as = ImmutableRoleListResponse.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class RoleListResponse extends BaseRoleResponse {
}
