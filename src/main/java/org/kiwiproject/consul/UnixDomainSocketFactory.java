package org.kiwiproject.consul;

import com.google.common.annotations.VisibleForTesting;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Path;

/**
 * A {@link SocketFactory} that creates connections to a local Consul agent
 * via a Unix domain socket instead of TCP.
 * <p>
 * All {@code createSocket} overloads ignore the host and port arguments and
 * connect to the configured socket path.
 * <p>
 * The no-arg {@link #createSocket()} returns an unconnected socket wrapper
 * whose {@code connect()} method redirects to the Unix socket path. This is
 * required because OkHttp calls the no-arg {@code createSocket()} and then
 * calls {@code connect(InetSocketAddress)} separately; returning an
 * already-connected {@code AFUNIXSocket} there would cause junixsocket to
 * attempt (and fail) to map the TCP address via {@code AFSocketAddress.mapOrFail}.
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
     * Returns a socket wrapper backed by an unconnected {@link AFUNIXSocket}.
     * The wrapper's {@code connect()} methods ignore the supplied address and
     * always connect to {@link #socketPath}, so that OkHttp's standard
     * {@code createSocket()} + {@code connect(InetSocketAddress)} flow works
     * correctly with Unix domain sockets.
     */
    @Override
    public Socket createSocket() throws IOException {
        var unixSocket = newAFUNIXSocket();
        var unixAddress = AFUNIXSocketAddress.of(socketPath);
        return new Socket() {
            @Override
            public void connect(SocketAddress endpoint) throws IOException {
                unixSocket.connect(unixAddress);
            }

            @Override
            public void connect(SocketAddress endpoint, int timeout) throws IOException {
                unixSocket.connect(unixAddress, timeout);
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return unixSocket.getInputStream();
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                return unixSocket.getOutputStream();
            }

            @Override
            public boolean isConnected() {
                return unixSocket.isConnected();
            }

            @Override
            public boolean isClosed() {
                return unixSocket.isClosed();
            }

            @Override
            public boolean isInputShutdown() {
                return unixSocket.isInputShutdown();
            }

            @Override
            public boolean isOutputShutdown() {
                return unixSocket.isOutputShutdown();
            }

            @Override
            public synchronized void close() throws IOException {
                unixSocket.close();
            }
        };
    }

    /**
     * Creates a new, unconnected {@link AFUNIXSocket}.
     */
    @VisibleForTesting
    protected AFUNIXSocket newAFUNIXSocket() throws IOException {
        return AFUNIXSocket.newInstance();
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
