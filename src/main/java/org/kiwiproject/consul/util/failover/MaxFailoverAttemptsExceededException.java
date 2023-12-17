package org.kiwiproject.consul.util.failover;

/**
 * Thrown when the maximum number of failover attempts is exceeded.
 */
public class MaxFailoverAttemptsExceededException extends RuntimeException {

    public MaxFailoverAttemptsExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
