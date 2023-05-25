package com.orbitz.consul.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UrlsTest {

    @Test
    void shouldCreateNewUrl_FromString() {
        var urlString = "https://github.com/kiwiproject/consul-client";
        var url = Urls.newUrl(urlString);

        assertEquals(urlString, url.toString());
    }

    @Test
    void shouldCreateNewUrl_FromString_AndThrow_WhenMalformed() {
        assertThrows(UncheckedMalformedURLException.class, () -> Urls.newUrl("oops"));
    }

    @Test
    void shouldCreateNewUrl_FromComponents() {
        var url = Urls.newUrl("https", "github.com", 443);

        assertEquals("https://github.com:443", url.toString());
    }

    @Test
    void shouldCreateNewUrl_FromComponents_AndThrow_WhenMalformed() {
        assertThrows(UncheckedMalformedURLException.class,
                () -> Urls.newUrl("bad_protocol", "github.com", 8080));
    }
}
