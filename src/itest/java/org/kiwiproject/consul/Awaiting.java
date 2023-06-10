package org.kiwiproject.consul;

import static org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_SECOND;

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

    /**
     * Awaits up to 500ms with a 25ms poll interval.
     */
    public static ConditionFactory awaitAtMost500ms() {
        return awaitWith25MsPoll().atMost(FIVE_HUNDRED_MILLISECONDS);
    }

    /**
     * Awaits up to 1s with a 25ms poll interval.
     */
    public static ConditionFactory awaitAtMost1s() {
        return awaitWith25MsPoll().atMost(ONE_SECOND);
    }

    public static ConditionFactory awaitWith25MsPoll() {
        return awaitWithPollingMs(25);
    }

    public static ConditionFactory awaitWithPollingMs(long millis) {
        return Awaitility.await().pollInterval(Duration.ofMillis(millis));
    }
}
