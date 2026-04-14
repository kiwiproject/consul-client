package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;

@DisplayName("UnixDomainSocketFactory")
class UnixDomainSocketFactoryTest {

    private static final Path TEST_SOCKET_PATH = Path.of("/tmp/test-consul.sock");

    private AFUNIXSocket mockUnixSocket;
    private Socket mockConnectedSocket;
    private TestableUnixDomainSocketFactory factory;

    @BeforeEach
    void setUp() {
        mockUnixSocket = mock(AFUNIXSocket.class);
        mockConnectedSocket = mock(Socket.class);
        factory = new TestableUnixDomainSocketFactory(TEST_SOCKET_PATH, mockUnixSocket, mockConnectedSocket);
    }

    /**
     * Subclass that overrides the two protected seam methods so tests run without
     * native junixsocket resources and without an actual socket file on disk.
     */
    static class TestableUnixDomainSocketFactory extends UnixDomainSocketFactory {

        private final AFUNIXSocket mockUnixSocket;
        private final Socket mockConnectedSocket;

        TestableUnixDomainSocketFactory(Path socketPath,
                                        AFUNIXSocket mockUnixSocket,
                                        Socket mockConnectedSocket) {
            super(socketPath);
            this.mockUnixSocket = mockUnixSocket;
            this.mockConnectedSocket = mockConnectedSocket;
        }

        @Override
        protected AFUNIXSocket newAFUNIXSocket() {
            return mockUnixSocket;
        }

        @Override
        protected Socket newConnectedSocket() {
            return mockConnectedSocket;
        }
    }

    @Nested
    class CreateSocket_NoArgs {

        private Socket wrapper;

        @BeforeEach
        void setUp() throws IOException {
            wrapper = factory.createSocket();
        }

        @Test
        void shouldReturnNonNullSocket() {
            assertThat(wrapper).isNotNull();
        }

        @Test
        void shouldDelegateConnect_ToUnixSocket() throws IOException {
            wrapper.connect(new InetSocketAddress("localhost", 8500));
            verify(mockUnixSocket).connect(any(AFUNIXSocketAddress.class));
        }

        @Test
        void shouldDelegateConnectWithTimeout_ToUnixSocket() throws IOException {
            wrapper.connect(new InetSocketAddress("localhost", 8500), 5_000);
            verify(mockUnixSocket).connect(any(AFUNIXSocketAddress.class), eq(5_000));
        }

        @Test
        void shouldDelegateGetInputStream() throws IOException {
            wrapper.getInputStream();
            verify(mockUnixSocket).getInputStream();
        }

        @Test
        void shouldDelegateGetOutputStream() throws IOException {
            wrapper.getOutputStream();
            verify(mockUnixSocket).getOutputStream();
        }

        @Test
        void shouldDelegateIsConnected() {
            wrapper.isConnected();
            verify(mockUnixSocket).isConnected();
        }

        @Test
        void shouldDelegateIsClosed() {
            wrapper.isClosed();
            verify(mockUnixSocket).isClosed();
        }

        @Test
        void shouldDelegateIsInputShutdown() {
            wrapper.isInputShutdown();
            verify(mockUnixSocket).isInputShutdown();
        }

        @Test
        void shouldDelegateIsOutputShutdown() {
            wrapper.isOutputShutdown();
            verify(mockUnixSocket).isOutputShutdown();
        }

        @Test
        void shouldDelegateClose() throws IOException {
            wrapper.close();
            verify(mockUnixSocket).close();
        }
    }

    @Nested
    class CreateSocket_WithHostAndPort {

        @Test
        void shouldReturnConnectedSocket_WhenGivenHostAndPort() throws IOException {
            assertThat(factory.createSocket("localhost", 8500)).isSameAs(mockConnectedSocket);
        }

        @Test
        void shouldReturnConnectedSocket_WhenGivenHostPortAndLocalAddress() throws IOException {
            assertThat(factory.createSocket("localhost", 8500, InetAddress.getLoopbackAddress(), 0))
                    .isSameAs(mockConnectedSocket);
        }

        @Test
        void shouldReturnConnectedSocket_WhenGivenInetAddressAndPort() throws IOException {
            assertThat(factory.createSocket(InetAddress.getLoopbackAddress(), 8500))
                    .isSameAs(mockConnectedSocket);
        }

        @Test
        void shouldReturnConnectedSocket_WhenGivenTwoInetAddressesAndPorts() throws IOException {
            var addr = InetAddress.getLoopbackAddress();
            assertThat(factory.createSocket(addr, 8500, addr, 0)).isSameAs(mockConnectedSocket);
        }
    }
}
