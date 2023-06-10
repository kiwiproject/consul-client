package org.kiwiproject.consul;

import static java.util.Objects.isNull;

import okhttp3.ResponseBody;
import retrofit2.Response;

import java.io.IOException;

/**
 * Wraps an exception thrown whilst interacting with the Consul API.
 */
public class ConsulException extends RuntimeException {

    private final int code;
    private final boolean hasCode;

    /**
     * Constructs an instance of this class.
     *
     * @param message The exception message.
     */
    public ConsulException(String message) {
        super(message);
        this.code = 0;
        this.hasCode = false;
    }

    /**
     * Constructs an instance of this class.
     *
     * @param message The exception message.
     * @param throwable The wrapped {@link Throwable} object.
     */
    public ConsulException(String message, Throwable throwable) {
        super(message, throwable);
        this.code = 0;
        this.hasCode = false;
    }

    public ConsulException(int code, Response<?> response) {
        super(String.format("Consul request failed with status [%s]: %s",
                code, message(response)));
        this.code = code;
        this.hasCode = true;
    }

    public ConsulException(Throwable throwable) {
        super("Consul request failed", throwable);
        this.code = 0;
        this.hasCode = false;
    }

    static String message(Response<?> response) {
        try {
            ResponseBody responseBody = response.errorBody();
            return isNull(responseBody) ? response.message() : responseBody.string();
        } catch (IOException e) {
            return response.message();
        }
    }

    /**
     * Check whether this exception is known to have been caused by an HTTP error.
     *
     * @return true if this exception was definitely caused by an HTTP error, otherwise false
     * @see #getCode()
     */
    public boolean hasCode() {
        return hasCode;
    }

    /**
     * Get the HTTP status code that caused this exception, if any.
     *
     * @return the HTTP error code, or zero if not known to have been caused by an HTTP error
     * @see #hasCode()
     */
    public int getCode() {
        return code;
    }
}
