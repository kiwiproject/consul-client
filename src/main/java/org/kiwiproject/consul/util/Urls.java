package org.kiwiproject.consul.util;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.jspecify.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Urls {

    private Urls() {
        // utility class
    }

    public static URL newUrl(String urlString) {
        try {
            return new URI(urlString).toURL();
        } catch (URISyntaxException e) {
            throw new UncheckedURISyntaxException(e);
        } catch (MalformedURLException e) {
            throw new UncheckedMalformedURLException(e);
        }
    }

    public static URL newUrl(String scheme, String host, int port) {
        return newUrl(scheme, host, port, null);
    }

    public static URL newUrl(String scheme, String host, int port, @Nullable String file) {
        try {
            var base = new URI(scheme, null, host, port, null, null, null);
            if (isBlank(file)) {
                return base.toURL();
            }

            return base.resolve(file).toURL();
        } catch (URISyntaxException e) {
            throw new UncheckedURISyntaxException(e);
        } catch (MalformedURLException e) {
            throw new UncheckedMalformedURLException(e);
        }
    }
}
