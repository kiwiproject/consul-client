package com.orbitz.consul.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TrustManagerUtilsTest {
    @Test
    void shouldTrustManagerReturnCorrectResult() {
        assertNotNull(TrustManagerUtils.getDefaultTrustManager());
    }
}
