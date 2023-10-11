package org.kiwiproject.consul.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.kiwiproject.consul.model.event.Event;

import java.math.BigInteger;
import java.util.List;

@Value.Immutable
@Value.Style(jakarta = true)
@JsonSerialize(as = ImmutableEventResponse.class)
@JsonDeserialize(as = ImmutableEventResponse.class)
public abstract class EventResponse {

    @Value.Parameter
    public abstract List<Event> getEvents();
    @Value.Parameter
    public abstract BigInteger getIndex();

}
