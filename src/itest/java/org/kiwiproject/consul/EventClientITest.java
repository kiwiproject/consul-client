package org.kiwiproject.consul;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;
import static org.kiwiproject.consul.Awaiting.awaitAtMost1s;
import static org.kiwiproject.consul.Awaiting.awaitWith25MsPoll;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.async.EventResponseCallback;
import org.kiwiproject.consul.model.EventResponse;
import org.kiwiproject.consul.model.event.Event;
import org.kiwiproject.consul.option.Options;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Note that in the tests which list events, we have to assume there might have
 * been previous events created. So, in those tests, we can't just make an assertion
 * about ALL events. Instead, we have to make assertions about events that we have
 * just created in the test.
 */
class EventClientITest extends BaseIntegrationTest {

    private EventClient eventClient;

    @BeforeEach
    void setUp() {
        eventClient = client.eventClient();
    }

    @Test
    void shouldFire() {
        var name = randomEventName();
        var firedEvent = eventClient.fireEvent(name);

        awaitWith25MsPoll()
                .atMost(TWO_HUNDRED_MILLISECONDS)
                .until(() -> eventIsFound(firedEvent.getId(), name));
    }

    @Test
    void shouldFireWithPayload() {
        var name = randomEventName();
        var payload = RandomStringUtils.randomAlphabetic(20);
        var firedEvent = eventClient.fireEvent(name, payload);

        var foundEventRef = new AtomicReference<Event>();
        awaitWith25MsPoll()
                .atMost(TWO_HUNDRED_MILLISECONDS)
                .until(() -> eventIsFound(firedEvent.getId(), name, foundEventRef));

        assertThat(foundEventRef.get()).isNotNull();
        assertThat(foundEventRef.get().getPayload()).contains(payload);
    }

    @Test
    void shouldListEvents() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        var eventResponse = eventClient.listEvents();

        var foundEventIds = getEventIds(eventResponse);
        assertThat(foundEventIds).hasSizeGreaterThanOrEqualTo(eventCount).containsAll(eventIds);
    }

    @Test
    void shouldListEventsByName() {
        var eventCount = 10;
        var events = createRandomEvents(eventCount);

        var index = currentRandom().nextInt(0, eventCount);
        var event1 = events.get(index);
        var name = event1.getName();

        // add another with the same name
        var event2 = eventClient.fireEvent(name);

        var eventResponse = eventClient.listEvents(name);
        assertThat(eventResponse.getEvents())
                .describedAs("events should be in order of creation using the LTime (Lamport time)")
                .extracting(Event::getId)
                .containsExactly(event1.getId(), event2.getId());
    }

    @Test
    void shouldListEventsUsingQueryOptions() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        var eventResponse = eventClient.listEvents(Options.BLANK_QUERY_OPTIONS);

        var foundEventIds = getEventIds(eventResponse);
        assertThat(foundEventIds).hasSizeGreaterThanOrEqualTo(eventCount).containsAll(eventIds);
    }

    @Test
    void shouldListEventsAsyncWithCallback() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        var callback = new TestEventResponseCallback();
        eventClient.listEvents(callback);

        awaitAtMost1s().until(() -> callback.getCompleteCount() >= eventCount);
        assertThat(callback.getFailureCount()).isZero();

        var completedEventIds = getEventIds(callback);
        assertThat(completedEventIds).hasSizeGreaterThanOrEqualTo(eventCount).containsAll(eventIds);
    }

    @Test
    void shouldListEventsAsyncWithQueryOptionsAndCallback() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        var callback = new TestEventResponseCallback();
        eventClient.listEvents(Options.BLANK_QUERY_OPTIONS, callback);

        awaitAtMost1s().until(() -> callback.getCompleteCount() >= eventCount);
        assertThat(callback.getFailureCount()).isZero();

        var completedEventIds = getEventIds(callback);
        assertThat(completedEventIds).hasSizeGreaterThanOrEqualTo(eventCount).containsAll(eventIds);
    }

    @Test
    void shouldListEventsAsyncWithNameAndQueryOptionsAndCallback() {
        var eventCount = randomNumberOfEvents();
        var events = createRandomEvents(eventCount);

        var index = currentRandom().nextInt(0, eventCount);
        var event = events.get(index);
        var name = event.getName();

        var callback = new TestEventResponseCallback();
        eventClient.listEvents(name, Options.BLANK_QUERY_OPTIONS, callback);

        awaitAtMost1s().until(() -> callback.getCompleteCount() == 1);
        assertThat(callback.getFailureCount()).isZero();

        var completedEventIds = getEventIds(callback);
        assertThat(completedEventIds).containsExactly(event.getId());
    }

    private List<String> createRandomEventsGettingIds(int eventCount) {
        return createRandomEventsAsStream(eventCount).map(Event::getId).toList();
    }

    private List<Event> createRandomEvents(int eventCount) {
        return createRandomEventsAsStream(eventCount).toList();
    }

    private Stream<Event> createRandomEventsAsStream(int eventCount) {
        return IntStream.range(0, eventCount)
                .mapToObj(ignored -> eventClient.fireEvent(randomEventName()));
    }

    List<String> getEventIds(EventResponse eventResponse) {
        return eventResponse.getEvents().stream().map(Event::getId).toList();
    }

    List<String> getEventIds(TestEventResponseCallback callback) {
        return getEventIds(getEvents(callback));
    }

    List<String> getEventIds(Collection<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    Collection<Event> getEvents(TestEventResponseCallback callback) {
        return callback.getCompletedEvents();
    }

    static class TestEventResponseCallback implements EventResponseCallback {

        private final ConcurrentMap<String, Event> completedEventsMap = new ConcurrentHashMap<>();
        private final AtomicInteger completeCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();

        @Override
        public void onComplete(EventResponse eventResponse) {
            completeCount.addAndGet(eventResponse.getEvents().size());
            eventResponse.getEvents().forEach(event -> completedEventsMap.put(event.getName(), event));
        }

        @Override
        public void onFailure(Throwable throwable) {
            failureCount.incrementAndGet();
        }

        Collection<Event> getCompletedEvents() {
            return completedEventsMap.values();
        }

        int getCompleteCount() {
            return completeCount.get();
        }

        int getFailureCount() {
            return failureCount.get();
        }
    }

    private static int randomNumberOfEvents() {
        return currentRandom().nextInt(2, 11);
    }

    private static String randomEventName() {
        var length = currentRandom().nextInt(10, 21);
        return RandomStringUtils.randomAlphanumeric(length);
    }

    private static ThreadLocalRandom currentRandom() {
        return ThreadLocalRandom.current();
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

        return nonNull(foundEventOrNull);
    }
}
