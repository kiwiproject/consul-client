package com.orbitz.consul.util;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class TrustManagerUtilsTest {
    @Test
    public void shouldTrustManagerReturnCorrectResult() {
        assertNotNull(TrustManagerUtils.getDefaultTrustManager());
    }
}
