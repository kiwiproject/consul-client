package com.orbitz.consul;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.config.ClientConfig;
import org.junit.After;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseIntegrationTest.class);

    private final List<String> deregisterServices = new CopyOnWriteArrayList<>();

    protected static Consul client;

    public static GenericContainer<?> consulContainer;
    static {
        consulContainer = new GenericContainer<>("consul")
            .withCommand("agent", "-dev", "-client", "0.0.0.0", "--enable-script-checks=true")
            .withExposedPorts(8500);
        consulContainer.start();
    }
    public static GenericContainer<?> consulContainerAcl;
    static {
        consulContainerAcl = new GenericContainer<>("consul")
            .withCommand("agent", "-dev", "-client", "0.0.0.0", "--enable-script-checks=true")
            .withExposedPorts(8500)
            .withEnv("CONSUL_LOCAL_CONFIG",
                    "{\n" +
                    "  \"acl\": {\n" +
                    "    \"enabled\": true,\n" +
                    "    \"default_policy\": \"deny\",\n" +
                    "    \"tokens\": {\n" +
                    "      \"master\": \"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}"
            );
        consulContainerAcl.start();
    }

    protected static HostAndPort defaultClientHostAndPort;

    @BeforeClass
    public static void beforeClass() {
        defaultClientHostAndPort = HostAndPort.fromParts("localhost", consulContainer.getFirstMappedPort());
        client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withClientConfiguration(new ClientConfig(CacheConfig.builder().withWatchDuration(Duration.ofSeconds(1)).build()))
                .withReadTimeoutMillis(Duration.ofSeconds(2).toMillis())
                .withWriteTimeoutMillis(Duration.ofMillis(500).toMillis())
                .build();
    }

    @After
    public void after() {
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
            "Unable to degister service. The serviceId was created using" +
            " createAutoDeregisterServiceId, but maybe it should not have been." +
            " For example, using the /agent/services endpoint only returns" +
            " services registered against the specific local agent with" +
            " which you are communicating. Message from the ConsulException: [%s]",
            e.getMessage());
    }

    protected String createAutoDeregisterServiceId() {
        String serviceId = UUID.randomUUID().toString();
        LOG.info("Created auto-deregister serviceId {}", serviceId);
        deregisterServices.add(serviceId);

        return serviceId;
    }
}
