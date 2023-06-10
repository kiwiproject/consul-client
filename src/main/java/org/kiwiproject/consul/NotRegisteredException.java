package org.kiwiproject.consul;

/**
 * Thrown when a previously registered service attempts to
 * check in.  Indicates an agent has been restarted and left
 * the cluster.
 * <p>
 * Typically, clients should re-register themselves in this
 * case.
 */
public class NotRegisteredException extends Exception {
    public NotRegisteredException(String message) {
        super(message);
    }
    public NotRegisteredException(String message, Throwable cause) {
        super(message, cause);
    }
}
