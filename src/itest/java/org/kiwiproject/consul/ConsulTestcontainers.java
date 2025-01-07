package org.kiwiproject.consul;

import static java.util.Objects.isNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsulTestcontainers {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulTestcontainers.class);

    private ConsulTestcontainers() {
        // utility class
    }

    /**
     * The name of the environment variable that defines the Consul Docker image version.
     * <p>
     * If you want to run the tests against a specific Consul Docker image, set this
     * environment variable to the version you want. It should match a valid tag name
     * in the hashicorp/consul
     * <a href="https://hub.docker.com/r/hashicorp/consul/tags">container images</a>
     * such as {@code "latest"}, {@code "1.20"}, or a more specific version such as {@code "1.19.1"}.
     */
    public static final String CONSUL_DOCKER_IMAGE_VERSION_ENV_VAR = "CONSUL_IMAGE_VERSION";

    /**
     * The name of the Consul Docker container image to use, e.g., {@code "hashicorp/consul:latest"}
     * or {@code "hashicorp/consul:1.20"}.
     * <p>
     * By default, this will be {@code "hashicorp/consul:latest"}. To change the value, you can
     * set the {@code CONSUL_IMAGE_VERSION} environment variable to the <em>tag name</em> that you
     * want to use, such as {@code "1.18.2"}. The prefix is automatically prepended to the
     * version.
     *
     * @see #CONSUL_DOCKER_IMAGE_VERSION_ENV_VAR
     */
    public static final String CONSUL_DOCKER_IMAGE_NAME = consulImageNameFromEnvOrLatest();

    private static String consulImageNameFromEnvOrLatest() {
        var value = System.getenv(CONSUL_DOCKER_IMAGE_VERSION_ENV_VAR);
        var consulVersion = isNull(value)  ? "latest" : value;
        var imageName = "hashicorp/consul:" + consulVersion;
        LOG.info("Using Consul container image: {}", imageName);
        return imageName;
    }
}
