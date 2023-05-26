package com.orbitz.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrustManagerUtilsTest {
    @Test
    void shouldTrustManagerReturnCorrectResult() {
        assertThat(TrustManagerUtils.getDefaultTrustManager()).isNotNull();
    }
}
