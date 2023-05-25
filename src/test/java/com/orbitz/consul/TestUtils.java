package com.orbitz.consul;

import java.util.UUID;

public class TestUtils {

    private TestUtils() {
        // utility class
    }

    public static String randomUUIDString() {
        return UUID.randomUUID().toString();
    }
}
