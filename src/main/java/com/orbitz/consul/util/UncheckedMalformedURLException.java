package com.orbitz.consul.util;

import static java.util.Objects.requireNonNull;

import java.net.MalformedURLException;

/**
 * Wraps a {@link MalformedURLException} with an unchecked exception.
 *
 * @implNote This is copied from <a href="https://github.com/kiwiproject/kiwi">kiwi</a>. In the future
 * we may add kiwi as a dependency to this library, and then this can be replaced with the one in kiwi.
 */
public class UncheckedMalformedURLException extends RuntimeException {

    public UncheckedMalformedURLException(MalformedURLException cause) {
        super(requireNonNull(cause));
    }

    public UncheckedMalformedURLException(String message, MalformedURLException cause) {
        super(message, requireNonNull(cause));
    }

    @Override
    public synchronized MalformedURLException getCause() {
        return (MalformedURLException) super.getCause();
    }
}
