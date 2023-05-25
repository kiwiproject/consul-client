package com.orbitz.consul;

import com.orbitz.consul.model.event.Event;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static com.orbitz.consul.Awaiting.awaitWith25MsPoll;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;

class EventITest extends BaseIntegrationTest {

    private EventClient eventClient;

    @BeforeEach
    void setUp() {
        eventClient = client.eventClient();
    }

    @Test
    void shouldFire() throws InterruptedException {
        var name = RandomStringUtils.random(10, true, true);
        var firedEvent = eventClient.fireEvent(name);

        awaitWith25MsPoll()
                .atMost(TWO_HUNDRED_MILLISECONDS)
                .until(() -> eventIsFound(firedEvent.getId(), name));
    }

    @Test
    void shouldFireWithPayload() throws InterruptedException {
        var payload = RandomStringUtils.randomAlphabetic(20);
        var name = RandomStringUtils.randomAlphabetic(10);
        var firedEvent = eventClient.fireEvent(name, payload);

        var foundEventRef = new AtomicReference<Event>();
        awaitWith25MsPoll()
                .atMost(TWO_HUNDRED_MILLISECONDS)
                .until(() -> eventIsFound(firedEvent.getId(), name, foundEventRef));

        assertThat(foundEventRef.get()).isNotNull();
        assertThat(foundEventRef.get().getPayload().get()).isEqualTo(payload);
    }

    private boolean eventIsFound(String eventId, String name) {
        return eventIsFound(eventId, name, new AtomicReference<>());
    }

    private boolean eventIsFound(String eventId, String name, AtomicReference<Event> foundEvent) {
        var events = eventClient.listEvents().getEvents();

        var foundEventOrNull = events.stream()
            .filter(event -> event.getId().equals(eventId) && event.getName().equals(name))
            .findFirst()
            .orElse(null);

        foundEvent.set(foundEventOrNull);

        return nonNull(foundEvent);
    }
}
