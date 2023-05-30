package com.orbitz.consul.option;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Options")
class OptionsTest {

    @Nested
    class From {

        @Test
        void shouldReturnEmptyMap_WhenGivenNoOptions() {
            assertThat(Options.from()).isEmpty();
        }

        @Test
        void shouldIncludeAllOptionsInParamAdder() {
            var queryOptions = ImmutableQueryOptions.builder()
                    .token("12345")
                    .consistencyMode(ConsistencyMode.CONSISTENT)
                    .namespace("ns1")
                    .datacenter("dc42")
                    .reason("why not?")
                    .build();

            var options = Options.from(queryOptions);

            assertThat(options).containsAllEntriesOf(queryOptions.toQuery());
        }

        @Test
        void shouldIgnoreNullParamAdders() {
            var queryOptions = ImmutableQueryOptions.builder()
                    .token("12345")
                    .consistencyMode(ConsistencyMode.CONSISTENT)
                    .namespace("ns1")
                    .datacenter("dc42")
                    .reason("why not?")
                    .build();

            var options = Options.from(null, queryOptions, null);

            assertThat(options).containsAllEntriesOf(queryOptions.toQuery());
        }
    }
}
