package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConsulClients")
class ConsulClientsTest {

    @Nested
    class DcQuery {

        @Test
        void shouldReturnEmptyMap_WhenGivenBlankString() {
            assertThat(ConsulClients.dcQuery(null)).isEmpty();
        }

        @Test
        void shouldReturnMapContainingSingleDcEntry_WhenGiven() {
            assertThat(ConsulClients.dcQuery("east"))
                .containsExactly(entry("dc", "east"));
        }
    }
}
