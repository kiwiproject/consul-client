package org.kiwiproject.consul.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CacheDescriptorTest {

    @Nested
    class ConstructorsAndGetters {

        @Test
        void shouldCreateWithEndpointOnly() {
            var descriptor = new CacheDescriptor("catalog.service");

            assertAll(
                    () -> assertThat(descriptor.getEndpoint()).isEqualTo("catalog.service"),
                    () -> assertThat(descriptor.getKey()).isNull()
            );
        }

        @Test
        void shouldCreateWithEndpointAndKey() {
            var descriptor = new CacheDescriptor("catalog.service", "myService");

            assertAll(
                    () -> assertThat(descriptor.getEndpoint()).isEqualTo("catalog.service"),
                    () -> assertThat(descriptor.getKey()).isEqualTo("myService")
            );
        }
    }

    @Nested
    class ToStringMethod {

        @Test
        void shouldReturnEndpointWhenKeyIsNull() {
            var descriptor = new CacheDescriptor("catalog.service");

            assertThat(descriptor).hasToString("catalog.service");
        }

        @Test
        void shouldReturnEndpointAndKeyWhenKeyIsNotNull() {
            var descriptor = new CacheDescriptor("catalog.service", "myService");

            assertThat(descriptor).hasToString("catalog.service \"myService\"");
        }
    }
}
