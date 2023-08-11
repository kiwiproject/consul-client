package org.kiwiproject.consul;

import static org.kiwiproject.consul.ConsulTestcontainers.CONSUL_DOCKER_IMAGE_NAME;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.config.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseIntegrationTest.class);

    private final List<String> deregisterServices = new CopyOnWriteArrayList<>();

    protected static Consul client;

    public static final GenericContainer<?> consulContainer;

    // NOTE:
    // The following starts a consul container that will persist across all tests
    // that extend this base class. Presumably it was done this way so that a single
    // container is used instead of each test creating one. But, this way doesn't
    // provide a way to shut the container down cleanly after all tests have run.
    // We might want to consider using a test suite for all tests that extend this
    // base class.

    static {
        // noinspection resource
        consulContainer = new GenericContainer<>(CONSUL_DOCKER_IMAGE_NAME)
                .withCommand("agent", "-dev", "-client", "0.0.0.0", "--enable-script-checks=true")
                .withExposedPorts(8500);
        consulContainer.start();
    }

    protected static HostAndPort defaultClientHostAndPort;

    @BeforeAll
    static void beforeAll() {
        defaultClientHostAndPort = HostAndPort.fromParts("localhost", consulContainer.getFirstMappedPort());
        client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withClientConfiguration(new ClientConfig(CacheConfig.builder().withWatchDuration(Duration.ofSeconds(1)).build()))
                .withReadTimeoutMillis(Duration.ofSeconds(2).toMillis())
                .withWriteTimeoutMillis(Duration.ofMillis(500).toMillis())
                .build();
    }

    @AfterEach
    void afterEach() {
        try {
            deregisterServices.forEach(client.agentClient()::deregister);
        } catch (ConsulException e) {
            String message = createDeregistrationErrorMessage(e);
            LOG.warn(message, e.getMessage());
            throw new RuntimeException(message, e);
        } finally {
            deregisterServices.clear();
        }
    }

    private static String createDeregistrationErrorMessage(ConsulException e) {
        return String.format(
            """
            	Unable to degister service. The serviceId was created using
            	 createAutoDeregisterServiceId, but maybe it should not have been.\
            	 For example, using the /agent/services endpoint only returns\
            	 services registered against the specific local agent with\
            	 which you are communicating. Message from the ConsulException: [%s]""",
            e.getMessage());
    }

    protected String createAutoDeregisterServiceId() {
        var serviceId = randomUUIDString();
        LOG.info("Created auto-deregister serviceId {}", serviceId);
        deregisterServices.add(serviceId);

        return serviceId;
    }
}
