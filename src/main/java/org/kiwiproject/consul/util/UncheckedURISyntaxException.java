package org.kiwiproject.consul.util;

import static java.util.Objects.requireNonNull;

import java.net.URISyntaxException;

/**
 * Wraps a {@link URISyntaxException} with an unchecked exception.
 *
 * @implNote This is copied from <a href="https://github.com/kiwiproject/kiwi">kiwi</a>.
 * In the future, we may add kiwi as a dependency to this library, and then this can be replaced with the one in kiwi.
 */
public class UncheckedURISyntaxException extends RuntimeException {

    public UncheckedURISyntaxException(String message, URISyntaxException cause) {
        super(message, requireNonNull(cause));
    }

    public UncheckedURISyntaxException(URISyntaxException cause) {
        super(requireNonNull(cause));
    }

    @Override
    public synchronized URISyntaxException getCause() {
        return (URISyntaxException) super.getCause();
    }
}
