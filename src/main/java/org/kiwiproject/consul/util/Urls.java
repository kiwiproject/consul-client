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
            var urlFileComponents = parseAsUrlFile(file);
            var uri = new URI(
                    scheme,
                    null,
                    host,
                    port,
                    urlFileComponents.path(),
                    urlFileComponents.query(),
                    urlFileComponents.fragment()
            );
            return uri.toURL();
        } catch (URISyntaxException e) {
            throw new UncheckedURISyntaxException(e);
        } catch (MalformedURLException e) {
            throw new UncheckedMalformedURLException(e);
        }
    }

    private record UrlFileComponents(@Nullable String path, @Nullable String query, @Nullable String fragment) {
    }

    private static UrlFileComponents parseAsUrlFile(@Nullable String file) {
        String path = file;
        String query = null;
        String fragment = null;

        if (isBlank(file)) {
            return new UrlFileComponents(null, null, null);
        }

        // Strip fragment
        var fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            fragment = path.substring(fragmentIndex + 1);
            path = path.substring(0, fragmentIndex);
        }

        // Strip query
        var queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            query = path.substring(queryIndex + 1);
            path = path.substring(0, queryIndex);
        }

        return new UrlFileComponents(path, query, fragment);
    }
}
