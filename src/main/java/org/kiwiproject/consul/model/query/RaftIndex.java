package org.kiwiproject.consul.model.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonDeserialize(as = ImmutableRaftIndex.class)
@JsonSerialize(as = ImmutableRaftIndex.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RaftIndex {
}
