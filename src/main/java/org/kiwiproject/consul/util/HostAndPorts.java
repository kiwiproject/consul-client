package org.kiwiproject.consul.util;

import com.google.common.net.HostAndPort;
import okhttp3.HttpUrl;
import okhttp3.Request;

/**
 * Utilities related to {@link HostAndPort}.
 */
public class HostAndPorts {

    private HostAndPorts() {
        // utility class
    }

    /**
     * Create a new {@link HostAndPort} instance from the given OkHttp {@link Request}.
     *
     * @param request the OkHttp request
     * @return a new {@link HostAndPort} instance
     */
    public static HostAndPort hostAndPortFromOkHttpRequest(Request request) {
        HttpUrl url = request.url();
        return HostAndPort.fromParts(url.host(), url.port());
    }
}
