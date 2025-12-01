package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlsTest {

    @Test
    void shouldCreateNewUrl_FromString() {
        var urlString = "https://github.com/kiwiproject/consul-client";
        var url = Urls.newUrl(urlString);

        assertThat(url).hasToString(urlString);
    }

    @Test
    void createNewUrl_FromString_ShouldThrow_WhenNotAbsolute() {
        assertThatIllegalArgumentException().isThrownBy(() -> Urls.newUrl("oops"));
    }

    @Test
    void createNewUrl_FromString_ShouldThrow_WhenBadURISyntax() {
        assertThatExceptionOfType(UncheckedURISyntaxException.class)
                .isThrownBy(() -> Urls.newUrl("bad_protocol://acme.com:9876"));
    }

    @Test
    void createNewUrl_FromString_ShouldThrow_WhenMalformed() {
        assertThatExceptionOfType(UncheckedMalformedURLException.class)
                .isThrownBy(() -> Urls.newUrl("nosuchscheme://example.com"));
    }

    @Test
    void shouldCreateNewUrl_FromComponents() {
        var url = Urls.newUrl("https", "github.com", 443);

        assertThat(url).hasToString("https://github.com:443");
    }

    @Test
    void createNewUrl_FromString_ShouldThrow_WhenPathNotAbsolute() {
        assertThatExceptionOfType(UncheckedURISyntaxException.class)
                .isThrownBy(() -> Urls.newUrl("https", "acme.com", 61000, "path"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "  " })
    void shouldCreateNewUrl_FromComponents_AndNoFile(String file) {
        var url = Urls.newUrl("https", "acme.com", 443, file);

        assertThat(url).hasToString("https://acme.com:443");
    }

    @Test
    void shouldCreateNewUrl_FromComponents_AndFile() {
        var url = Urls.newUrl("https", "acme.com", 443, "/some-path");

        assertThat(url).hasToString("https://acme.com:443/some-path");
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            /foo
            /foo?bar=baz
            /foo?bar=baz&a=b
            /foo#a1
            /foo#b1
            /foo?bar=baz#a1
            """)
    void shouldCreateNewUrl_FromVariousFileArguments(String file) {
        var url = Urls.newUrl("https", "acme.com", 8443, file);

        assertThat(url).hasToString("https://acme.com:8443" + file);
    }

    @Test
    void createNewUrl_FromComponents_ShouldThrow_WhenBadURISyntax() {
        assertThatExceptionOfType(UncheckedURISyntaxException.class)
                .isThrownBy(() -> Urls.newUrl("bad_protocol", "github.com", 8080));
    }

    @Test
    void createNewUrl_FromComponents_ShouldThrow_WhenMalformed() {
        assertThatExceptionOfType(UncheckedMalformedURLException.class)
                .isThrownBy(() -> Urls.newUrl("nosuchscheme", "acme.com", 9876));
    }
}
