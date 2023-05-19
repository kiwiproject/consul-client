package com.orbitz.consul.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class UrlsTest {

    @Test
    public void shouldCreateNewUrl_FromString() {
        var urlString = "https://github.com/kiwiproject/consul-client";
        var url = Urls.newUrl(urlString);

        assertEquals(urlString, url.toString());
    }

    @Test
    public void shouldCreateNewUrl_FromString_AndThrow_WhenMalformed() {
        assertThrows(UncheckedMalformedURLException.class, () -> Urls.newUrl("oops"));
    }

    @Test
    public void shouldCreateNewUrl_FromComponents() {
        var url = Urls.newUrl("https", "github.com", 443);

        assertEquals("https://github.com:443", url.toString());
    }

    @Test
    public void shouldCreateNewUrl_FromComponents_AndThrow_WhenMalformed() {
        assertThrows(UncheckedMalformedURLException.class,
                () -> Urls.newUrl("bad_protocol", "github.com", 8080));
    }
}
