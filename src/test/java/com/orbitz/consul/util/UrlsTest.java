package com.orbitz.consul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import org.junit.jupiter.api.Test;

class UrlsTest {

    @Test
    void shouldCreateNewUrl_FromString() {
        var urlString = "https://github.com/kiwiproject/consul-client";
        var url = Urls.newUrl(urlString);

        assertThat(url.toString()).isEqualTo(urlString);
    }

    @Test
    void shouldCreateNewUrl_FromString_AndThrow_WhenMalformed() {
        assertThatExceptionOfType(UncheckedMalformedURLException.class).isThrownBy(() -> Urls.newUrl("oops"));
    }

    @Test
    void shouldCreateNewUrl_FromComponents() {
        var url = Urls.newUrl("https", "github.com", 443);

        assertThat(url.toString()).isEqualTo("https://github.com:443");
    }

    @Test
    void shouldCreateNewUrl_FromComponents_AndThrow_WhenMalformed() {
        assertThatExceptionOfType(UncheckedMalformedURLException.class).isThrownBy(() -> Urls.newUrl("bad_protocol", "github.com", 8080));
    }
}
