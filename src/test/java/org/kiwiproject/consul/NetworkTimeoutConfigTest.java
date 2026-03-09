package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NetworkTimeoutConfig")
class NetworkTimeoutConfigTest {

    @Nested
    class BuilderTest {

        @Test
        void shouldUseDefaultValues_WhenNoTimeoutsConfigured() {
            var config = new NetworkTimeoutConfig.Builder().build();

            assertThat(config.getClientReadTimeoutMillis()).isEqualTo(-1);
            assertThat(config.getClientWriteTimeoutMillis()).isEqualTo(-1);
            assertThat(config.getClientConnectTimeoutMillis()).isEqualTo(-1);
        }

        @Test
        void shouldSetReadTimeout_WithIntValue() {
            var config = new NetworkTimeoutConfig.Builder()
                    .withReadTimeout(5000)
                    .build();

            assertThat(config.getClientReadTimeoutMillis()).isEqualTo(5000);
        }

        @Test
        void shouldSetReadTimeout_WithIntSupplier() {
            var config = new NetworkTimeoutConfig.Builder()
                    .withReadTimeout(() -> 3000)
                    .build();

            assertThat(config.getClientReadTimeoutMillis()).isEqualTo(3000);
        }

        @Test
        void shouldSetWriteTimeout_WithIntValue() {
            var config = new NetworkTimeoutConfig.Builder()
                    .withWriteTimeout(6000)
                    .build();

            assertThat(config.getClientWriteTimeoutMillis()).isEqualTo(6000);
        }

        @Test
        void shouldSetWriteTimeout_WithIntSupplier() {
            var config = new NetworkTimeoutConfig.Builder()
                    .withWriteTimeout(() -> 4000)
                    .build();

            assertThat(config.getClientWriteTimeoutMillis()).isEqualTo(4000);
        }

        @Test
        void shouldSetConnectTimeout_WithIntValue() {
            var config = new NetworkTimeoutConfig.Builder()
                    .withConnectTimeout(2000)
                    .build();

            assertThat(config.getClientConnectTimeoutMillis()).isEqualTo(2000);
        }

        @Test
        void shouldSetConnectTimeout_WithIntSupplier() {
            var config = new NetworkTimeoutConfig.Builder()
                    .withConnectTimeout(() -> 1000)
                    .build();

            assertThat(config.getClientConnectTimeoutMillis()).isEqualTo(1000);
        }

        @Test
        void shouldSetAllTimeouts() {
            var config = new NetworkTimeoutConfig.Builder()
                    .withReadTimeout(5000)
                    .withWriteTimeout(6000)
                    .withConnectTimeout(2000)
                    .build();

            assertThat(config.getClientReadTimeoutMillis()).isEqualTo(5000);
            assertThat(config.getClientWriteTimeoutMillis()).isEqualTo(6000);
            assertThat(config.getClientConnectTimeoutMillis()).isEqualTo(2000);
        }

        @Test
        void shouldEvaluateSupplierOnEachCall() {
            var counter = new int[]{0};
            var config = new NetworkTimeoutConfig.Builder()
                    .withReadTimeout(() -> ++counter[0] * 1000)
                    .build();

            assertThat(config.getClientReadTimeoutMillis()).isEqualTo(1000);
            assertThat(config.getClientReadTimeoutMillis()).isEqualTo(2000);
        }
    }
}
