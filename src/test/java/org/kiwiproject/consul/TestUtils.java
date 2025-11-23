package org.kiwiproject.consul;

import com.google.common.io.Resources;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.IntStream;

public class TestUtils {

    private static final int MAX_PORT_NUMBER = 65_535;

    private TestUtils() {
        // utility class
    }

    public static String randomUUIDString() {
        return UUID.randomUUID().toString();
    }

    public static String fixture(String resourceName) {
        return fixture(resourceName, StandardCharsets.UTF_8);
    }

    public static String fixture(String resourceName, Charset charset) {
        Throwable t;
        try {
            var url = Resources.getResource(resourceName);
            var path = Paths.get(url.toURI());
            return Files.readString(path, charset);
        } catch (Exception e) {
            t = e;
        } catch (Error e) {
            // Because of bug JDK-8286287, we must catch Error. This bug is fixed in JDK 19.
            // See also:
            // https://bugs.openjdk.org/browse/JDK-8286287
            // https://stackoverflow.com/questions/72127702/error-which-shouldnt-happen-caused-by-malformedinputexception-when-reading-fi
            t = e;
        }

        var message = String.format("Unable to load resource %s using charset %s", resourceName, charset);
        throw new RuntimeException(message, t);
    }

    public static int findFirstOpenPortFromOrThrow(int startPort) {
        checkValidPort(startPort);

        return IntStream.rangeClosed(startPort, MAX_PORT_NUMBER)
                .filter(TestUtils::isPortAvailable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No open port available starting at " + startPort));
    }

    // Copied with slight modifications from kiwi's LocalPortChecker.
    public static boolean isPortAvailable(int port) {
        checkValidPort(port);

        try (var serverSocket = new java.net.ServerSocket(port);
             var datagramSocket = new java.net.DatagramSocket(port)) {
            serverSocket.setReuseAddress(true);
            datagramSocket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void checkValidPort(int port) {
        if (port < 1 || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }
}
