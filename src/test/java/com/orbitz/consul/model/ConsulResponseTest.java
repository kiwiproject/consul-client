package com.orbitz.consul.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConsulResponseTest {

    @Nested
    class Constrcutors {

        @Test
        void shouldAllowCreatingFromCacheMiss() {
            var cacheMissResponse = new ConsulResponse<>(null, 0, false, null, "MISS", null);
            assertThat(cacheMissResponse.getCacheReponseInfo()).isPresent();

            var cacheResponseInfo = cacheMissResponse.getCacheReponseInfo().orElseThrow();
            assertThat(cacheResponseInfo.isCacheHit()).isFalse();
            assertThat(cacheResponseInfo.getAgeInSeconds()).isEmpty();
        }

        @Test
        void shouldAllowCreatingFromCacheHit() {
            var cacheHitResponse = new ConsulResponse<>(null, 0, false, null, "HIT", "42");
            assertThat(cacheHitResponse.getCacheReponseInfo()).isPresent();

            var cacheResponseInfo = cacheHitResponse.getCacheReponseInfo().orElseThrow();
            assertThat(cacheResponseInfo.isCacheHit()).isTrue();
            assertThat(cacheResponseInfo.getAgeInSeconds()).contains(42L);
        }
    }
}
