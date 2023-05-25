package com.orbitz.consul.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.security.GeneralSecurityException;

import org.junit.jupiter.api.Test;
import java.security.NoSuchAlgorithmException;

class UncheckedGeneralSecurityExceptionTest {

    @Test
    void testConstructWithMessage() {
        var cause = newGeneralSecurityException();
        var exception = new UncheckedGeneralSecurityException("nope", cause);

        assertEquals("nope", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testConstructWithoutMessage() {
        var cause = newGeneralSecurityException();
        var exception = new UncheckedGeneralSecurityException(cause);

        assertThat(exception.getMessage(), containsString(cause.getMessage()));
        assertSame(cause, exception.getCause());
    }

    private static GeneralSecurityException newGeneralSecurityException() {
        return new NoSuchAlgorithmException("bad algorithm: FooBar123");
    }
}
