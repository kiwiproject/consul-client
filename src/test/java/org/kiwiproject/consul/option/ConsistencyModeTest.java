package org.kiwiproject.consul.option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class ConsistencyModeTest {

    @Test
    void checkCompatibilityWithOldEnum() {
        assertThat(ConsistencyMode.values()).hasSize(3);
        for (int i = 0; i < ConsistencyMode.values().length; i++) {
            assertThat(i).isEqualTo(ConsistencyMode.values()[i].ordinal());
        }
        assertThat(ConsistencyMode.values()[0]).isEqualTo(ConsistencyMode.DEFAULT);
        assertThat(ConsistencyMode.values()[0].name()).isEqualTo("DEFAULT");
        assertThat(ConsistencyMode.values()[1]).isEqualTo(ConsistencyMode.STALE);
        assertThat(ConsistencyMode.values()[1].name()).isEqualTo("STALE");
        assertThat(ConsistencyMode.values()[2]).isEqualTo(ConsistencyMode.CONSISTENT);
        assertThat(ConsistencyMode.values()[2].name()).isEqualTo("CONSISTENT");
    }

    @Test
    void checkHeadersForCached_WithOptionals() {
        var consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(30L), Optional.of(60L));
        assertThat(consistency.toParam()).contains("cached");
        assertThat(consistency.getAdditionalHeaders()).hasSize(1);
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "max-age=30,stale-if-error=60");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(30L), Optional.empty());
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "max-age=30");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.of(60L));
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "stale-if-error=60");

        // Consistency cache without Cache-Control directives
        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.empty());
        assertThat(consistency.toParam()).contains("cached");
        assertThat(consistency.getAdditionalHeaders()).isEmpty();
    }

    @Test
    void checkHeadersForCached() {
        var consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(30L, 60L);
        assertThat(consistency.toParam()).contains("cached");
        assertThat(consistency.getAdditionalHeaders()).hasSize(1);
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "max-age=30,stale-if-error=60");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(30L, null);
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "max-age=30");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(null, 60L);
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "stale-if-error=60");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(0L, null);
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "max-age=0");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(null, 0L);
        assertThat(consistency.getAdditionalHeaders()).containsEntry("Cache-Control", "stale-if-error=0");

        // Consistency cache without Cache-Control directives
        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(null, (Long) null);
        assertThat(consistency.toParam()).contains("cached");
        assertThat(consistency.getAdditionalHeaders()).isEmpty();
    }

    @Test
    void checkBadMaxAge_WithOptionals() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(-1L), Optional.empty()));
    }

    @Test
    void checkBadMaxAge() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(-1L, null));
    }

    @Test
    void checkBadMaxStaleError_WithOptionals() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.of(-2L)));
    }

    @Test
    void checkBadMaxStaleError() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(null, -2L));
    }

    @Test
    void shouldHaveToString_WithOptionals() {
        var maxAgeSeconds = Optional.of(30L);
        var maxStaleSeconds = Optional.of(60L);
        var consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(maxAgeSeconds, maxStaleSeconds);

        assertThat(consistency.toString()).contains("CACHED[Cache-Control=max-age=30,stale-if-error=60]");
    }

    @Test
    void shouldHaveToString() {
        var maxAgeSeconds = 30L;
        var maxStaleSeconds = 60L;
        var consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(maxAgeSeconds, maxStaleSeconds);

        assertThat(consistency.toString()).contains("CACHED[Cache-Control=max-age=30,stale-if-error=60]");
    }
}
