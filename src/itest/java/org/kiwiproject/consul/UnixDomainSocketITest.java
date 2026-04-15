package org.kiwiproject.consul;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.kiwiproject.consul.ConsulTestcontainers.CONSUL_DOCKER_IMAGE_NAME;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Integration test for Unix domain socket support.
 *
 * @implNote This test is Linux-only. Docker Desktop on macOS runs containers
 * inside a Linux VM; bind-mounted directories sync regular files but Unix domain
 * socket endpoints only exist in the VM's kernel, so a macOS host process cannot
 * connect() to them. Run this on Linux or in CI.
 */
@EnabledOnOs(OS.LINUX)
class UnixDomainSocketITest {

    private static final Logger LOG = LoggerFactory.getLogger(UnixDomainSocketITest.class);

    private static final String SOCKET_FILE_NAME = "consul.sock";
    private static final String CONTAINER_SOCKET_DIR = "/consul-socket";

    private static GenericContainer<?> consulContainer;
    private static Path socketDir;
    private static Path socketPath;
    private static Consul client;

    @BeforeAll
    @SuppressWarnings("resource")
    static void beforeAll() throws IOException {
        socketDir = Files.createTempDirectory("consul-uds-itest-");
        Files.setPosixFilePermissions(socketDir, PosixFilePermissions.fromString("rwxrwxrwx"));
        socketPath = socketDir.resolve(SOCKET_FILE_NAME);

        LOG.info("Socket dir: {} (permissions: {})",
                socketDir,
                Files.getPosixFilePermissions(socketDir));

        var socketAddress = CONTAINER_SOCKET_DIR + "/" + SOCKET_FILE_NAME;

        consulContainer = new GenericContainer<>(CONSUL_DOCKER_IMAGE_NAME)
                .withCommand("agent", "-dev")
                .withEnv("CONSUL_LOCAL_CONFIG", """
                        {
                            "addresses": {
                                "http": "unix://%s"
                            },
                            "unix_sockets": {
                                "mode": "0777"
                            }
                        }
                        """.formatted(socketAddress))
                .withFileSystemBind(socketDir.toAbsolutePath().toString(),
                        CONTAINER_SOCKET_DIR, BindMode.READ_WRITE)
                .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("consul"))
                .waitingFor(Wait.forLogMessage(".*agent: Synced node info.*", 1));

        consulContainer.start();

        try {
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> Files.exists(socketPath));
        } catch (ConditionTimeoutException e) {
            try (var entries = Files.list(socketDir)) {
                var contents = entries.map(Path::toString).collect(Collectors.joining(", "));
                LOG.error("Socket file not found at {} after 30s. Directory contents: [{}]",
                        socketPath, contents.isEmpty() ? "<empty>" : contents);
            }
            LOG.error("Container logs:\n{}", consulContainer.getLogs());
            throw e;
        }

        client = Consul.builder()
                .withUnixDomainSocket(socketPath)
                .build();
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (nonNull(client)) {
            client.destroy();
        }
        if (nonNull(consulContainer)) {
            consulContainer.stop();
        }
        Files.deleteIfExists(socketPath);
        Files.deleteIfExists(socketDir);
    }

    @Test
    void shouldReturnLeader() {
        var leader = client.statusClient().getLeader();
        assertThat(leader).isNotBlank();
    }

    @Test
    void shouldRoundTripKeyValue() {
        var key = "uds-test/" + randomUUIDString();
        var value = "hello-from-uds";

        var kvClient = client.keyValueClient();
        kvClient.putValue(key, value);

        var retrieved = kvClient.getValueAsString(key);
        assertThat(retrieved).isPresent().hasValue(value);

        kvClient.deleteKey(key);
    }
}
