package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@DisplayName("UnixDomainSocketFactory")
class UnixDomainSocketFactoryTest {

    private static final Logger LOG = LoggerFactory.getLogger(UnixDomainSocketFactoryTest.class);

    private static final Path TEST_SOCKET_PATH = Path.of("/tmp/test-consul.sock");

    private Socket mockConnectedSocket;
    private TestableUnixDomainSocketFactory factory;

    @BeforeEach
    void setUp() {
        mockConnectedSocket = mock(Socket.class);
        factory = new TestableUnixDomainSocketFactory(TEST_SOCKET_PATH, mockConnectedSocket);
    }

    /**
     * Subclass that overrides the protected seam method so tests run without
     * native junixsocket resources and without an actual socket file on disk.
     */
    static class TestableUnixDomainSocketFactory extends UnixDomainSocketFactory {

        private final Socket mockConnectedSocket;

        TestableUnixDomainSocketFactory(Path socketPath, Socket mockConnectedSocket) {
            super(socketPath);
            this.mockConnectedSocket = mockConnectedSocket;
        }

        @Override
        protected Socket newConnectedSocket() {
            return mockConnectedSocket;
        }
    }

    @Nested
    class CreateSocket_WithHostAndPort {

        @Test
        void shouldReturnConnectedSocket_WhenGivenHostAndPort() throws IOException {
            assertThat(factory.createSocket("ignored-host", 0)).isSameAs(mockConnectedSocket);
        }

        @Test
        void shouldReturnConnectedSocket_WhenGivenHostPortAndLocalAddress() throws IOException {
            assertThat(factory.createSocket("ignored-host", 0, InetAddress.getLoopbackAddress(), 0))
                    .isSameAs(mockConnectedSocket);
        }

        @Test
        void shouldReturnConnectedSocket_WhenGivenInetAddressAndPort() throws IOException {
            assertThat(factory.createSocket(InetAddress.getLoopbackAddress(), 0))
                    .isSameAs(mockConnectedSocket);
        }

        @Test
        void shouldReturnConnectedSocket_WhenGivenTwoInetAddressesAndPorts() throws IOException {
            var addr = InetAddress.getLoopbackAddress();
            assertThat(factory.createSocket(addr, 0, addr, 0)).isSameAs(mockConnectedSocket);
        }
    }

    @Nested
    class WithRealServer {

        private Path socketPath;
        private ServerSocketChannel serverChannel;
        private Thread acceptThread;

        @BeforeEach
        void setUp() throws IOException {
            // Use /tmp directly — java.io.tmpdir on macOS expands to a long /var/folders/...
            // path. Unix domain socket paths are limited to 104 bytes on macOS (the
            // sockaddr_un sun_path field in sys/un.h) and 108 bytes on Linux.
            socketPath = Path.of("/tmp", "consul-uds-" + UUID.randomUUID().toString().substring(0, 8) + ".sock");

            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            LOG.info("Test server listening on: {}", socketPath);

            acceptThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        serverChannel.accept().close();
                    }
                } catch (IOException e) {
                    // server channel closed — expected on teardown
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        @AfterEach
        void tearDown() throws IOException {
            acceptThread.interrupt();
            serverChannel.close();
            Files.deleteIfExists(socketPath);
        }

        @Test
        void shouldReturnConnectedSocket_WhenNoArgs() throws IOException {
            var realFactory = new UnixDomainSocketFactory(socketPath);
            try (var socket = realFactory.createSocket()) {
                socket.connect(new InetSocketAddress("ignored-host", 0));
                assertThat(socket.isConnected()).isTrue();
            }
        }

        @Test
        void shouldReturnConnectedSocket_WhenGivenHostAndPort() throws IOException {
            var realFactory = new UnixDomainSocketFactory(socketPath);
            try (var socket = realFactory.createSocket("ignored-host", 0)) {
                assertThat(socket.isConnected()).isTrue();
            }
        }
    }
}
