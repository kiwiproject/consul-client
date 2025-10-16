package org.kiwiproject.consul;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.consul.Awaiting.awaitAtMost2s;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.async.EventResponseCallback;
import org.kiwiproject.consul.model.EventResponse;
import org.kiwiproject.consul.model.event.Event;
import org.kiwiproject.consul.option.Options;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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

        awaitAtMost2s().until(() -> eventIsFound(firedEvent.getId(), name));
    }

    @Test
    void shouldFireWithPayload() {
        var name = randomEventName();
        var payload = RandomStringUtils.secure().nextAlphabetic(20);
        var firedEvent = eventClient.fireEvent(name, payload);

        var foundEventRef = new AtomicReference<Event>();
        awaitAtMost2s().until(() -> eventIsFound(firedEvent.getId(), name, foundEventRef));

        assertThat(foundEventRef.get()).isNotNull();
        assertThat(foundEventRef.get().getPayload()).contains(payload);
    }

    @Test
    void shouldListEvents() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        awaitAtMost2s().untilAsserted(() -> {
            var eventResponse = eventClient.listEvents();
            var foundEventIds = getEventIds(eventResponse);
            assertThat(foundEventIds).containsAll(eventIds);
        });
    }

    @Test
    void shouldListEventsByName() {
        var eventCount = 10;
        var events = createRandomEvents(eventCount);

        var index = currentRandom().nextInt(0, eventCount);
        var event1 = events.get(index);
        var name = event1.getName();

        // fire several more events with the same name
        eventClient.fireEvent(name);
        eventClient.fireEvent(name);
        eventClient.fireEvent(name);

        awaitAtMost2s().untilAsserted(() ->
                assertThat(eventClient.listEvents(name).getEvents())
                        .extracting(Event::getName)
                        .containsOnly(name)
        );
    }

    @Test
    void shouldListEventsUsingQueryOptions() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        awaitAtMost2s().untilAsserted(() -> {
            var eventResponse = eventClient.listEvents(Options.BLANK_QUERY_OPTIONS);
            var foundEventIds = getEventIds(eventResponse);
            assertThat(foundEventIds).containsAll(eventIds);
        });
    }

    @Test
    void shouldListEventsAsyncWithCallback() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        var callback = new TestEventResponseCallback();
        eventClient.listEvents(callback);

        awaitAtMost2s().untilAsserted(() ->
                assertThat(callback.getEventIds()).containsAll(eventIds));

        assertThat(callback.getFailureCount()).isZero();
    }

    @Test
    void shouldListEventsAsyncWithQueryOptionsAndCallback() {
        var eventCount = randomNumberOfEvents();
        var eventIds = createRandomEventsGettingIds(eventCount);

        var callback = new TestEventResponseCallback();
        eventClient.listEvents(Options.BLANK_QUERY_OPTIONS, callback);

        awaitAtMost2s().untilAsserted(() ->
                assertThat(callback.getEventIds()).containsAll(eventIds));

        assertThat(callback.getFailureCount()).isZero();
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

        awaitAtMost2s().untilAsserted(() ->
                assertThat(callback.getEventIds()).contains(event.getId()));

        assertThat(callback.getFailureCount()).isZero();
    }

    @Test
    void shouldInvokeOnFailureWhenConsulIsUnreachable() throws Exception {
        // Reserve an unused loopback port, then close it so nothing is listening there
        int freePort;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            freePort = socket.getLocalPort();
        }

        // Build a Consul client pointing at the now-unused port on localhost
        var badEventClient = Consul.builder()
                .withUrl("http://127.0.0.1:" + freePort)
                .withConnectTimeoutMillis(50)
                .withReadTimeoutMillis(50)
                .withPing(false)
                .build()
                .eventClient();

        var callback = new TestEventResponseCallback();
        badEventClient.listEvents(callback);

        awaitAtMost2s().untilAsserted(() ->
                assertThat(callback.getFailureCount()).isPositive());
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

    private static Set<String> getEventIds(EventResponse eventResponse) {
        return eventResponse.getEvents().stream().map(Event::getId).collect(toUnmodifiableSet());
    }

    static class TestEventResponseCallback implements EventResponseCallback {

        private final ConcurrentMap<String, Event> completedEventsMap = new ConcurrentHashMap<>();
        private final AtomicInteger failureCount = new AtomicInteger();

        @Override
        public void onComplete(EventResponse eventResponse) {
            eventResponse.getEvents().forEach(event -> completedEventsMap.put(event.getId(), event));
        }

        @Override
        public void onFailure(Throwable throwable) {
            failureCount.incrementAndGet();
        }

        Collection<Event> getCompletedEvents() {
            return completedEventsMap.values();
        }

        Set<String> getEventIds() {
            return getCompletedEvents().stream().map(Event::getId).collect(toUnmodifiableSet());
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
        return RandomStringUtils.secure().nextAlphabetic(length);
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
