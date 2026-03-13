package org.kiwiproject.consul;

import java.util.function.IntSupplier;

/**
 * Configuration for network timeouts used by cacheable consul clients.
 * <p>
 * This class was extracted from its former location as a nested class inside
 * {@link Consul} to avoid circular dependencies between {@link Consul} and
 * the cacheable client classes ({@link BaseCacheableClient} and its subclasses).
 */
public class NetworkTimeoutConfig {
    private final IntSupplier readTimeoutMillisSupplier;
    private final IntSupplier writeTimeoutMillisSupplier;
    private final IntSupplier connectTimeoutMillisSupplier;

    NetworkTimeoutConfig(
            IntSupplier readTimeoutMillisSupplier,
            IntSupplier writeTimeoutMillisSupplier,
            IntSupplier connectTimeoutMillisSupplier) {
        this.readTimeoutMillisSupplier = readTimeoutMillisSupplier;
        this.writeTimeoutMillisSupplier = writeTimeoutMillisSupplier;
        this.connectTimeoutMillisSupplier = connectTimeoutMillisSupplier;
    }

    public int getClientReadTimeoutMillis() {
        return readTimeoutMillisSupplier.getAsInt();
    }

    public int getClientWriteTimeoutMillis() {
        return writeTimeoutMillisSupplier.getAsInt();
    }

    public int getClientConnectTimeoutMillis() {
        return connectTimeoutMillisSupplier.getAsInt();
    }

    public static class Builder {
        private IntSupplier readTimeoutMillisSupplier = () -> -1;
        private IntSupplier writeTimeoutMillisSupplier = () -> -1;
        private IntSupplier connectTimeoutMillisSupplier = () -> -1;

        public Builder withReadTimeout(IntSupplier timeoutSupplier) {
            this.readTimeoutMillisSupplier = timeoutSupplier;
            return this;
        }

        public Builder withReadTimeout(int millis) {
            return withReadTimeout(() -> millis);
        }

        public Builder withWriteTimeout(IntSupplier timeoutSupplier) {
            this.writeTimeoutMillisSupplier = timeoutSupplier;
            return this;
        }

        public Builder withWriteTimeout(int millis) {
            return withWriteTimeout(() -> millis);
        }

        public Builder withConnectTimeout(IntSupplier timeoutSupplier) {
            this.connectTimeoutMillisSupplier = timeoutSupplier;
            return this;
        }

        public Builder withConnectTimeout(int millis) {
            return withConnectTimeout(() -> millis);
        }

        public NetworkTimeoutConfig build() {
            return new NetworkTimeoutConfig(readTimeoutMillisSupplier, writeTimeoutMillisSupplier, connectTimeoutMillisSupplier);
        }
    }
}
