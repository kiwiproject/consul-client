package com.orbitz.consul.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

public class UncheckedGeneralSecurityExceptionTest {

    @Test
    public void testConstructWithMessage() {
        var cause = newGeneralSecurityException();
        var exception = new UncheckedGeneralSecurityException("nope", cause);

        assertEquals("nope", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    public void testConstructWithoutMessage() {
        var cause = newGeneralSecurityException();
        var exception = new UncheckedGeneralSecurityException(cause);

        assertThat(exception.getMessage(), containsString(cause.getMessage()));
        assertSame(cause, exception.getCause());
    }

    private static GeneralSecurityException newGeneralSecurityException() {
        return new NoSuchAlgorithmException("bad algorithm: FooBar123");
    }
}
