package org.kiwiproject.consul;

import com.google.common.annotations.VisibleForTesting;
import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;

/**
 * A {@link SocketFactory} that creates connections to a local Consul agent
 * via a Unix domain socket instead of TCP.
 * <p>
 * All {@code createSocket} overloads ignore the host and port arguments and
 * connect to the configured socket path.
 * <p>
 * The no-arg {@link #createSocket()} delegates to
 * {@link AFSocketFactory.FixedAddressSocketFactory}, which returns a real
 * {@link AFUNIXSocket} configured via {@code forceConnectAddress}. This means
 * OkHttp's standard {@code createSocket()} + {@code connect(InetSocketAddress)}
 * flow transparently connects to the Unix domain socket path, and all socket
 * options (including {@code setSoTimeout}) work natively on the underlying socket.
 * <p>
 * The overloads that accept a host/port return an already-connected socket
 * directly, since callers of those variants do not make a separate
 * {@code connect()} call.
 * <p>
 * Requires the {@code junixsocket-core} dependency on the classpath.
 *
 * @see Consul.Builder#withUnixDomainSocket(Path)
 * @see Consul.Builder#withUnixDomainSocket(String)
 */
public class UnixDomainSocketFactory extends SocketFactory {

    private final Path socketPath;

    /**
     * Creates a new factory that connects to the given Unix domain socket path.
     *
     * @param socketPath path to the Unix domain socket file
     */
    public UnixDomainSocketFactory(Path socketPath) {
        this.socketPath = socketPath;
    }

    /**
     * Returns an unconnected {@link AFUNIXSocket} whose {@code connect()} methods
     * ignore the supplied address and always connect to the configured socket path.
     * This allows OkHttp's standard {@code createSocket()} +
     * {@code connect(InetSocketAddress)} flow to work correctly with Unix domain sockets.
     */
    @Override
    public Socket createSocket() throws IOException {
        return new AFSocketFactory.FixedAddressSocketFactory(AFUNIXSocketAddress.of(socketPath))
                .createSocket();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return newConnectedSocket();
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return newConnectedSocket();
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return newConnectedSocket();
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return newConnectedSocket();
    }

    /**
     * Creates and returns an already-connected {@link AFUNIXSocket}.
     */
    @VisibleForTesting
    protected Socket newConnectedSocket() throws IOException {
        var address = AFUNIXSocketAddress.of(socketPath);
        return AFUNIXSocket.connectTo(address);
    }
}
