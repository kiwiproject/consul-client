package org.kiwiproject.consul.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConsulResponse")
class ConsulResponseTest {

    @Nested
    class Constructors {

        @SuppressWarnings("removal")
        @Test
        void shouldAllowCreatingFromCacheMiss() {
            var cacheMissResponse = new ConsulResponse<>(null, 0, false, null, "MISS", null);
            assertThat(cacheMissResponse.getCacheReponseInfo()).isPresent();
            assertThat(cacheMissResponse.getCacheResponseInfo()).isPresent();
            assertThat(cacheMissResponse.getCacheReponseInfo()).isSameAs(cacheMissResponse.getCacheResponseInfo());

            var cacheResponseInfo = cacheMissResponse.getCacheResponseInfo().orElseThrow();
            assertThat(cacheResponseInfo.isCacheHit()).isFalse();
            assertThat(cacheResponseInfo.getAgeInSeconds()).isEmpty();
        }

        @SuppressWarnings("removal")
        @Test
        void shouldAllowCreatingFromCacheHit() {
            var cacheHitResponse = new ConsulResponse<>(null, 0, false, null, "HIT", "42");
            assertThat(cacheHitResponse.getCacheReponseInfo()).isPresent();
            assertThat(cacheHitResponse.getCacheResponseInfo()).isPresent();
            assertThat(cacheHitResponse.getCacheReponseInfo()).isSameAs(cacheHitResponse.getCacheResponseInfo());

            var cacheResponseInfo = cacheHitResponse.getCacheReponseInfo().orElseThrow();
            assertThat(cacheResponseInfo.isCacheHit()).isTrue();
            assertThat(cacheResponseInfo.getAgeInSeconds()).contains(42L);
        }
    }
}
