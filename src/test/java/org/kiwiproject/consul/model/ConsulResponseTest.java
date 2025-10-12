package org.kiwiproject.consul.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
            assertThat(cacheMissResponse.getCacheReponseInfo()).isEqualTo(cacheMissResponse.getCacheResponseInfo());

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
            assertThat(cacheHitResponse.getCacheReponseInfo()).isEqualTo(cacheHitResponse.getCacheResponseInfo());

            var cacheResponseInfo = cacheHitResponse.getCacheReponseInfo().orElseThrow();
            assertThat(cacheResponseInfo.isCacheHit()).isTrue();
            assertThat(cacheResponseInfo.getAgeInSeconds()).contains(42L);
        }

        @SuppressWarnings("removal")
        @Test
        void shouldCreateUsingDeprecatedConstructor_WhenCacheResponseInfoExists() {
            var cacheHitResponse = new ConsulResponse<>(null,
                    0,
                    false,
                    null,
                    Optional.ofNullable(ConsulResponse.buildCacheResponseInfo("HIT", "0")));

            assertAll(
                    () -> assertThat(cacheHitResponse.getResponse()).isNull(),
                    () -> assertThat(cacheHitResponse.getLastContact()).isZero(),
                    () -> assertThat(cacheHitResponse.isKnownLeader()).isFalse(),
                    () -> assertThat(cacheHitResponse.getIndex()).isNull(),
                    () -> assertThat(cacheHitResponse.getCacheResponseInfo()).isPresent()
            );
        }

        @SuppressWarnings("removal")
        @Test
        void shouldCreateUsingDeprecatedConstructor_WhenCacheResponseInfo_DoesNotExist() {
            var cacheHitResponse = new ConsulResponse<>(null,
                    0,
                    false,
                    null,
                    Optional.ofNullable(ConsulResponse.buildCacheResponseInfo(null, null)));

            assertAll(
                    () -> assertThat(cacheHitResponse.getResponse()).isNull(),
                    () -> assertThat(cacheHitResponse.getLastContact()).isZero(),
                    () -> assertThat(cacheHitResponse.isKnownLeader()).isFalse(),
                    () -> assertThat(cacheHitResponse.getIndex()).isNull(),
                    () -> assertThat(cacheHitResponse.getCacheResponseInfo()).isEmpty()
            );
        }
    }

    @Nested
    class BuildCacheResponseInfo {

        @Test
        void shouldReturnNull_WhenHitMissCacheHeader_IsNull() {
            assertThat(ConsulResponse.buildCacheResponseInfo(null, null)).isNull();
        }

        @Test
        void shouldReturnValue_ForCacheHit() {
            var cacheResponseInfo = ConsulResponse.buildCacheResponseInfo("HIT", "0");
            assertThat(cacheResponseInfo).isNotNull();

            assertAll(
                    () -> assertThat(cacheResponseInfo.isCacheHit()).isTrue(),
                    () -> assertThat(cacheResponseInfo.getAgeInSeconds()).contains(0L)
            );
        }

        @Test
        void shouldReturnValue_ForCacheMiss() {
            var cacheResponseInfo = ConsulResponse.buildCacheResponseInfo(null, null);
            assertThat(cacheResponseInfo).isNull();
        }
    }
}
