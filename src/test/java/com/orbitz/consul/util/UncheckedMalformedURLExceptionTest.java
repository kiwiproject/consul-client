package com.orbitz.consul.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.net.MalformedURLException;

/**
 * @implNote Copied from  <a href="https://github.com/kiwiproject/kiwi">kiwi</a> and modified
 * because as of now, this library uses JUnit 4 and Hamcrest matchers.
 */
public class UncheckedMalformedURLExceptionTest {

    @Test
    public void testConstructWithMessage() {
        var cause = newMalformedURLException();
        var exception = new UncheckedMalformedURLException("nope", cause);

        assertEquals("nope", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    public void testConstructWithoutMessage() {
        var cause = newMalformedURLException();
        var exception = new UncheckedMalformedURLException(cause);

        assertThat(exception.getMessage(), containsString(cause.getMessage()));
        assertSame(cause, exception.getCause());
    }

    private static MalformedURLException newMalformedURLException() {
        return new MalformedURLException("bad URL");
    }
}
