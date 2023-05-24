package com.orbitz.consul;

import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.time.Duration;

/**
 * Test utilities for using {@link Awaitility}.
 */
public class Awaiting {

    private Awaiting() {
        // utility class
    }

    /**
     * Awaits up to 100ms with a 25ms poll interval.
     */
    public static ConditionFactory awaitAtMost100ms() {
        return awaitWith25MsPoll().atMost(ONE_HUNDRED_MILLISECONDS);
    }

    public static ConditionFactory awaitWith25MsPoll() {
        return awaitWithPollingMs(25);
    }

    public static ConditionFactory awaitWithPollingMs(long millis) {
        return Awaitility.await().pollInterval(Duration.ofMillis(millis));
    }
}
