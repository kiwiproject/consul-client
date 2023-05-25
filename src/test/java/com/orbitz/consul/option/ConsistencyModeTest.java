package com.orbitz.consul.option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ConsistencyModeTest {

    @Test
    void checkCompatinbilityWithOldEnum(){
        assertThat(ConsistencyMode.values().length).isEqualTo(3);
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
    void checkHeadersForCached() {
        ConsistencyMode consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(Long.valueOf(30)), Optional.of(60L));
        assertThat(consistency.toParam().get()).isEqualTo("cached");
        assertThat(consistency.getAdditionalHeaders().size()).isEqualTo(1);
        assertThat(consistency.getAdditionalHeaders().get("Cache-Control")).isEqualTo("max-age=30,stale-if-error=60");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(30L), Optional.empty());
        assertThat(consistency.getAdditionalHeaders().get("Cache-Control")).isEqualTo("max-age=30");

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.of(60L));
        assertThat(consistency.getAdditionalHeaders().get("Cache-Control")).isEqualTo("stale-if-error=60");

        // Consistency cache without Cache-Control directives
        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.empty());
        assertThat(consistency.toParam().get()).isEqualTo("cached");
        assertThat(consistency.getAdditionalHeaders().size()).isEqualTo(0);
    }

    @Test
    void checkBadMaxAge() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(-1L), Optional.empty());
        });
    }

    @Test
    void checkBadMaxStaleError() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.of(-2L));
        });
    }

    @Test
    void shouldHaveToString() {
        var maxAgeSeconds = Optional.of(Long.valueOf(30));
        var maxStaleSeconds = Optional.of(60L);
        var consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(maxAgeSeconds, maxStaleSeconds);

        assertThat(consistency.toString(), containsString("CACHED[Cache-Control=max-age=30,stale-if-error=60]"));
    }
}