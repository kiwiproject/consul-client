package org.kiwiproject.consul.util;

import java.net.MalformedURLException;
import java.net.URL;

public class Urls {

    private Urls() {
        // utility class
    }

    public static URL newUrl(String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            throw new UncheckedMalformedURLException(e);
        }
    }

    public static URL newUrl(String scheme, String host, int port) {
        return newUrl(scheme, host, port, "");
    }

    public static URL newUrl(String scheme, String host, int port, String file) {
        try {
            return new URL(scheme, host, port, file);
        } catch (MalformedURLException e) {
            throw new UncheckedMalformedURLException(e);
        }
    }
}
