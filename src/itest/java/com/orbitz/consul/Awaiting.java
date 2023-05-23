package com.orbitz.consul;

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

    public static ConditionFactory awaitWith25MsPoll() {
        return awaitWithPollingMs(25);
    }

    public static ConditionFactory awaitWithPollingMs(long millis) {
        return Awaitility.await().pollInterval(Duration.ofMillis(millis));
    }
}
