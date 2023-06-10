package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

class UncheckedGeneralSecurityExceptionTest {

    @Test
    void testConstructWithMessage() {
        var cause = newGeneralSecurityException();
        var exception = new UncheckedGeneralSecurityException("nope", cause);

        assertThat(exception.getMessage()).isEqualTo("nope");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void testConstructWithoutMessage() {
        var cause = newGeneralSecurityException();
        var exception = new UncheckedGeneralSecurityException(cause);

        assertThat(exception.getMessage()).contains(cause.getMessage());
        assertThat(exception.getCause()).isSameAs(cause);
    }

    private static GeneralSecurityException newGeneralSecurityException() {
        return new NoSuchAlgorithmException("bad algorithm: FooBar123");
    }
}
