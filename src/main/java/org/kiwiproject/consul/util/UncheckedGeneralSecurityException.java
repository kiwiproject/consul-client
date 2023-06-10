package org.kiwiproject.consul.util;

import static java.util.Objects.requireNonNull;

import java.security.GeneralSecurityException;

/**
 * Wraps a {@link GeneralSecurityException} with an unchecked exception.
 */
public class UncheckedGeneralSecurityException extends RuntimeException {

    public UncheckedGeneralSecurityException(GeneralSecurityException cause) {
        super(requireNonNull(cause));
    }

    public UncheckedGeneralSecurityException(String message, GeneralSecurityException cause) {
        super(message, requireNonNull(cause));
    }

    @Override
    public synchronized GeneralSecurityException getCause() {
        return (GeneralSecurityException) super.getCause();
    }
}
