package org.kiwiproject.consul;

/**
 * Provides an ACL token for authenticating requests to Consul.
 * <p>
 * Implementations are called on every request, allowing tokens to be
 * refreshed dynamically without rebuilding the {@link Consul} client.
 * This is useful when tokens expire and must be reloaded periodically.
 * <p>
 * For static tokens, use {@link Consul.Builder#withTokenAuth(String)},
 * which is implemented in terms of this interface.
 *
 * @see Consul.Builder#withTokenAuth(AuthTokenProvider)
 */
@FunctionalInterface
public interface AuthTokenProvider {

    /**
     * Returns the current ACL token to use for the next request.
     *
     * @return the token string
     */
    String getToken();
}
