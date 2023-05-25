package com.orbitz.consul.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.MalformedURLException;

import org.junit.jupiter.api.Test;

/**
 * @implNote Copied from  <a href="https://github.com/kiwiproject/kiwi">kiwi</a> and modified
 * because as of now, this library uses JUnit 4 and Hamcrest matchers.
 */
class UncheckedMalformedURLExceptionTest {

    @Test
    void testConstructWithMessage() {
        var cause = newMalformedURLException();
        var exception = new UncheckedMalformedURLException("nope", cause);

        assertEquals("nope", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testConstructWithoutMessage() {
        var cause = newMalformedURLException();
        var exception = new UncheckedMalformedURLException(cause);

        assertThat(exception.getMessage(), containsString(cause.getMessage()));
        assertSame(cause, exception.getCause());
    }

    private static MalformedURLException newMalformedURLException() {
        return new MalformedURLException("bad URL");
    }
}
