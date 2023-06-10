package org.kiwiproject.consul;

import com.google.common.io.Resources;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

public class TestUtils {

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
}
