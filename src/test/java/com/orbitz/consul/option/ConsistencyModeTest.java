package com.orbitz.consul.option;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Optional;

public class ConsistencyModeTest {

    @Test
    public void checkCompatinbilityWithOldEnum(){
        assertEquals(3, ConsistencyMode.values().length);
        for (int i = 0; i < ConsistencyMode.values().length; i++) {
            assertEquals(ConsistencyMode.values()[i].ordinal(), i);
        }
        assertEquals(ConsistencyMode.DEFAULT, ConsistencyMode.values()[0]);
        assertEquals("DEFAULT", ConsistencyMode.values()[0].name());
        assertEquals(ConsistencyMode.STALE, ConsistencyMode.values()[1]);
        assertEquals("STALE", ConsistencyMode.values()[1].name());
        assertEquals(ConsistencyMode.CONSISTENT, ConsistencyMode.values()[2]);
        assertEquals("CONSISTENT", ConsistencyMode.values()[2].name());
    }

    @Test
    public void checkHeadersForCached() {
        ConsistencyMode consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(Long.valueOf(30)), Optional.of(60L));
        assertEquals("cached", consistency.toParam().get());
        assertEquals(1, consistency.getAdditionalHeaders().size());
        assertEquals("max-age=30,stale-if-error=60", consistency.getAdditionalHeaders().get("Cache-Control"));

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(30L), Optional.empty());
        assertEquals("max-age=30", consistency.getAdditionalHeaders().get("Cache-Control"));

        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.of(60L));
        assertEquals("stale-if-error=60", consistency.getAdditionalHeaders().get("Cache-Control"));

        // Consistency cache without Cache-Control directives
        consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.empty());
        assertEquals("cached", consistency.toParam().get());
        assertEquals(0, consistency.getAdditionalHeaders().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkBadMaxAge() {
        ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(-1L), Optional.empty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkBadMaxStaleError() {
        ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.empty(), Optional.of(-2L));
    }

    @Test
    public void shouldHaveToString() {
        var maxAgeSeconds = Optional.of(Long.valueOf(30));
        var maxStaleSeconds = Optional.of(60L);
        var consistency = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(maxAgeSeconds, maxStaleSeconds);

        assertThat(consistency.toString(), containsString("CACHED[Cache-Control=max-age=30,stale-if-error=60]"));
    }
}