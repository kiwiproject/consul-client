package org.kiwiproject.consul;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.internal.Util;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.consul.cache.TimeoutInterceptor;
import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import org.kiwiproject.consul.monitoring.NoOpClientEventCallback;
import org.kiwiproject.consul.util.Jackson;
import org.kiwiproject.consul.util.TrustManagerUtils;
import org.kiwiproject.consul.util.Urls;
import org.kiwiproject.consul.util.bookend.ConsulBookend;
import org.kiwiproject.consul.util.bookend.ConsulBookendInterceptor;
import org.kiwiproject.consul.util.failover.ConsulFailoverInterceptor;
import org.kiwiproject.consul.util.failover.strategy.ConsulFailoverStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.net.Proxy;
import java.net.URL;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
* Client for interacting with the Consul HTTP API.
*
* @author rfast
*/
public class Consul {

    private static final Logger LOG = LoggerFactory.getLogger(Consul.class);

    /**
    * Default Consul HTTP API host.
    */
    public static final String DEFAULT_HTTP_HOST = "localhost";

    /**
    * Default Consul HTTP API port.
    */
    public static final int DEFAULT_HTTP_PORT = 8500;

    private final AgentClient agentClient;
    private final AclClient aclClient;
    private final HealthClient healthClient;
    private final KeyValueClient keyValueClient;
    private final CatalogClient catalogClient;
    private final StatusClient statusClient;
    private final SessionClient sessionClient;
    private final EventClient eventClient;
    private final PreparedQueryClient preparedQueryClient;
    private final CoordinateClient coordinateClient;
    private final OperatorClient operatorClient;
    private final SnapshotClient snapshotClient;

    private final ExecutorService executorService;
    private final ConnectionPool connectionPool;
    private final OkHttpClient okHttpClient;
    private boolean destroyed;


    /**
     * Package-private constructor.
     *
     * @param agentClient         the {@link AgentClient}
     * @param healthClient        the {@link HealthClient}
     * @param keyValueClient      the {@link KeyValueClient}
     * @param catalogClient       the {@link CatalogClient}
     * @param statusClient        the {@link StatusClient}
     * @param sessionClient       the {@link SessionClient}
     * @param eventClient         the {@link EventClient}
     * @param preparedQueryClient the {@link PreparedQueryClient}
     * @param coordinateClient    the {@link CoordinateClient}
     * @param operatorClient      the {@link OperatorClient}
     * @param executorService     the executor service provided to OkHttp
     * @param connectionPool      the OkHttp connection pool
     * @param aclClient           the {@link AclClient}
     * @param snapshotClient      the {@link SnapshotClient}
     * @param okHttpClient        the {@link OkHttpClient}
     */
    protected Consul(AgentClient agentClient,
                     HealthClient healthClient,
                     KeyValueClient keyValueClient,
                     CatalogClient catalogClient,
                     StatusClient statusClient,
                     SessionClient sessionClient,
                     EventClient eventClient,
                     PreparedQueryClient preparedQueryClient,
                     CoordinateClient coordinateClient,
                     OperatorClient operatorClient,
                     ExecutorService executorService,
                     ConnectionPool connectionPool,
                     AclClient aclClient,
                     SnapshotClient snapshotClient,
                     OkHttpClient okHttpClient) {
        this.agentClient = agentClient;
        this.healthClient = healthClient;
        this.keyValueClient = keyValueClient;
        this.catalogClient = catalogClient;
        this.statusClient = statusClient;
        this.sessionClient = sessionClient;
        this.eventClient = eventClient;
        this.preparedQueryClient = preparedQueryClient;
        this.coordinateClient = coordinateClient;
        this.operatorClient = operatorClient;
        this.executorService = executorService;
        this.connectionPool = connectionPool;
        this.aclClient = aclClient;
        this.snapshotClient = snapshotClient;
        this.okHttpClient = okHttpClient;
    }

    /**
    * Destroys the Object internal state.
    */
    public void destroy() {
        this.destroyed = true;
        this.okHttpClient.dispatcher().cancelAll();
        this.executorService.shutdownNow();
        this.connectionPool.evictAll();
    }

    /**
     * Check whether the internal state has been shut down.
     *
     * @return true if {@link #destroy()} was called, otherwise false
     */
    public boolean isDestroyed() {
        return destroyed;
    }

    /**
    * Get the Agent HTTP client.
    * <p>
    * /v1/agent
    *
    * @return The Agent HTTP client.
    */
    public AgentClient agentClient() {
        return agentClient;
    }

    /**
    * Get the ACL HTTP client.
    * <p>
    * /v1/acl
    *
    * @return The ACL HTTP client.
    */
    public AclClient aclClient() {
        return aclClient;
    }

    /**
    * Get the Catalog HTTP client.
    * <p>
    * /v1/catalog
    *
    * @return The Catalog HTTP client.
    */
    public CatalogClient catalogClient() {
        return catalogClient;
    }

    /**
    * Get the Health HTTP client.
    * <p>
    * /v1/health
    *
    * @return The Health HTTP client.
    */
    public HealthClient healthClient() {
        return healthClient;
    }

    /**
    * Get the Key/Value HTTP client.
    * <p>
    * /v1/kv
    *
    * @return The Key/Value HTTP client.
    */
    public KeyValueClient keyValueClient() {
        return keyValueClient;
    }

    /**
    * Get the Status HTTP client.
    * <p>
    * /v1/status
    *
    * @return The Status HTTP client.
    */
    public StatusClient statusClient() {
        return statusClient;
    }

    /**
    * Get the SessionInfo HTTP client.
    * <p>
    * /v1/session
    *
    * @return The SessionInfo HTTP client.
    */
    public SessionClient sessionClient() {
        return sessionClient;
    }

    /**
    * Get the Event HTTP client.
    * <p>
    * /v1/event
    *
    * @return The Event HTTP client.
    */
    public EventClient eventClient() {
        return eventClient;
    }

    /**
    * Get the Prepared Query HTTP client.
    * <p>
    * /v1/query
    *
    * @return The Prepared Query HTTP client.
    */
    public PreparedQueryClient preparedQueryClient() {
        return preparedQueryClient;
    }

    /**
    * Get the Coordinate HTTP client.
    * <p>
    * /v1/coordinate
    *
    * @return The Coordinate HTTP client.
    */
    public CoordinateClient coordinateClient() {
        return coordinateClient;
    }

    /**
    * Get the Operator HTTP client.
    * <p>
    * /v1/operator
    *
    * @return The Operator HTTP client.
    */
    public OperatorClient operatorClient() {
        return operatorClient;
    }

    /**
    * Get the Snapshot HTTP client.
    * <p>
    * /v1/snapshot
    *
    * @return The Snapshot HTTP client.
    */
    public SnapshotClient snapshotClient() {
        return snapshotClient;
    }

    /**
    * Creates a new {@link Builder} object.
    *
    * @return A new Consul builder.
    */
    public static Builder builder() {
        return new Builder();
    }

    /**
    * Used to create a default Consul client.
    *
    * @return A default {@link Consul} client.
    */
    @VisibleForTesting
    public static Consul newClient() {
        return builder().build();
    }

    /**
    * Builder for {@link Consul} client objects.
    */
    public static class Builder {

        private static final String NEGATIVE_VALUE = "Negative value";

        private String scheme = "http";
        private URL url;
        private SSLContext sslContext;
        private X509TrustManager trustManager;
        private HostnameVerifier hostnameVerifier;
        private Proxy proxy;
        private boolean ping = true;
        private Interceptor authInterceptor;
        private Interceptor aclTokenInterceptor;
        private Interceptor headerInterceptor;
        private Interceptor consulBookendInterceptor;
        private ConsulFailoverInterceptor consulFailoverInterceptor;
        private int numTimesConsulFailoverInterceptorSet;
        private int maxFailoverAttempts;
        private final NetworkTimeoutConfig.Builder networkTimeoutConfigBuilder = new NetworkTimeoutConfig.Builder();
        private ExecutorService executorService;
        private ConnectionPool connectionPool;
        private ClientConfig clientConfig;
        private ClientEventCallback clientEventCallback;

        /**
        * Constructs a new builder.
        */
        Builder() {
            url = Urls.newUrl(scheme, DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT);
        }

        /**
        * Sets the URL from a {@link URL} object.
        *
        * @param url The Consul agent URL.
        * @return The builder.
        */
        public Builder withUrl(URL url) {
            this.url = url;

            return this;
        }

        /**
         * Use HTTPS connections for all requests.
         *
         * @param withHttps Set to true to use https for all Consul requests.
         * @return The builder.
         */
        public Builder withHttps(boolean withHttps) {
            if (withHttps) {
                this.scheme = "https";
            } else {
                this.scheme = "http";
            }

            // if url was already generated from a call to withMultipleHostAndPort or withMultipleHostAndPort,
            // it might have the old scheme saved into url, so recreate it here if it has changed
            if (!this.url.getProtocol().equals(this.scheme)) {
                this.url = Urls.newUrl(scheme, this.url.getHost(), this.url.getPort());
            }
            return this;
        }

        /**
        * Instructs the builder that the AgentClient should attempt a ping before returning the Consul instance
        *
        * @param ping Whether the ping should be done or not
        * @return The builder.
        */
        public Builder withPing(boolean ping) {
            this.ping = ping;

            return this;
        }

        /**
        * Sets the username and password to be used for basic authentication
        *
        * @param username the value of the username
        * @param password the value of the password
        * @return The builder.
        */
        public Builder withBasicAuth(String username, String password) {
            String credentials = username + ":" + password;
            final String basic = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
            authInterceptor = chain -> {
                Request original = chain.request();

                Request.Builder requestBuilder = original.newBuilder()
                        .header("Authorization", basic)
                        .method(original.method(), original.body());

                Request request = requestBuilder.build();
                return chain.proceed(request);
            };

            return this;
        }

        /**
         * Sets the token used for authentication
         *
         * @param token the token
         * @return The builder.
         */
        public Builder withTokenAuth(String token) {
            authInterceptor = chain -> {
                Request original = chain.request();

                Request.Builder requestBuilder = original.newBuilder()
                        .header("X-Consul-Token", token)
                        .method(original.method(), original.body());

                Request request = requestBuilder.build();
                return chain.proceed(request);
            };

            return this;
        }

        /**
        * Sets the ACL token to be used with Consul
        *
        * @param token the value of the token
        * @return The builder.
        */
        public Builder withAclToken(final String token) {
            aclTokenInterceptor = chain -> {
                Request original = chain.request();

                HttpUrl originalUrl = original.url();
                String rewrittenUrl;
                if (originalUrl.queryParameterNames().isEmpty()) {
                    rewrittenUrl = originalUrl.url().toExternalForm() + "?token=" + token;
                } else {
                    rewrittenUrl = originalUrl.url().toExternalForm() + "&token=" + token;
                }

                Request.Builder requestBuilder = original.newBuilder()
                        .url(rewrittenUrl)
                        .method(original.method(), original.body());

                Request request = requestBuilder.build();
                return chain.proceed(request);
            };

            return this;
        }

        /**
        * Sets headers to be included with each Consul request.
        *
        * @param headers Map of headers.
        * @return The builder.
        */
        public Builder withHeaders(final Map<String, String> headers) {
            headerInterceptor = chain -> {
                Request.Builder requestBuilder = chain.request().newBuilder();

                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.addHeader(header.getKey(), header.getValue());
                }

                return chain.proceed(requestBuilder.build());
            };

            return this;
        }

        /**
         * Attaches a {@link ConsulBookend} to each Consul request. This can be used for gathering
         * metrics, timings or debugging.
         *
         * @param consulBookend The bookend implementation.
         * @return The builder.
         * @see ConsulBookend
         */
        public Builder withConsulBookend(ConsulBookend consulBookend) {
            consulBookendInterceptor = new ConsulBookendInterceptor(consulBookend);

            return this;
        }

        /**
        * Sets the URL from a {@link HostAndPort} object.
        *
        * @param hostAndPort The Consul agent host and port.
        * @return The builder.
        */
        public Builder withHostAndPort(HostAndPort hostAndPort) {
            this.url = Urls.newUrl(scheme, hostAndPort.getHost(), hostAndPort.getPort());

            return this;
        }

        /**
         * Uses the given failover interceptor.
         * <p>
         * Note that only one method that sets a {@link ConsulFailoverInterceptor} should be used when
         * constructing a Consul instance. Otherwise, only the last one is used.
         *
         * @param failoverInterceptor the failover interceptor to use
         * @return The builder.
         */
        public Builder withConsulFailoverInterceptor(ConsulFailoverInterceptor failoverInterceptor) {
            checkArgument(nonNull(failoverInterceptor), "failoverInterceptor must not be null");
            logWarningIfConsulFailoverInterceptorAlreadySet("withConsulFailoverInterceptor");

            this.consulFailoverInterceptor = failoverInterceptor;
            ++numTimesConsulFailoverInterceptorSet;
            return this;
        }

        /**
         * Sets the list of hosts to contact if the current request target is
         * unavailable. When the call to a particular URL fails for any reason, the next {@link HostAndPort} specified
         * is used to retry the request. This will continue until all urls are exhausted.
         * <p>
         * Internally, this method constructs a {@link ConsulFailoverInterceptor} with a
         * {@link org.kiwiproject.consul.util.failover.strategy.BlacklistingConsulFailoverStrategy BlacklistingConsulFailoverStrategy}.
         * The hosts and blacklist time are provided to the failover strategy.
         * <p>
         * Note that only one method that sets a {@link ConsulFailoverInterceptor} should be used when
         * constructing a Consul instance. Otherwise, only the last one is used.
         *
         * @param hostAndPort           A collection of {@link HostAndPort} that define the list of Consul agent addresses to use.
         * @param blacklistTimeInMillis The timeout (in milliseconds) to blacklist a particular {@link HostAndPort} before trying to use it again.
         * @return The builder.
         */
        public Builder withMultipleHostAndPort(Collection<HostAndPort> hostAndPort, long blacklistTimeInMillis) {
            checkArgument(blacklistTimeInMillis >= 0, "Blacklist time must be positive (or zero)");
            checkArgument(hostAndPort.size() >= 2, "Minimum of 2 addresses are required");
            logWarningIfConsulFailoverInterceptorAlreadySet("withMultipleHostAndPort");

            consulFailoverInterceptor = new ConsulFailoverInterceptor(hostAndPort, blacklistTimeInMillis);
            ++numTimesConsulFailoverInterceptorSet;
            withHostAndPort(hostAndPort.stream().findFirst().orElseThrow());

            return this;
        }

        /**
         * Sets the list of hosts to contact if the current request target is
         * unavailable. When the call to a particular URL fails for any reason, the next {@link HostAndPort} specified
         * is used to retry the request. This will continue until all urls are exhausted.
         * <p>
         * This method sets a {@link ConsulFailoverInterceptor} using the given hosts and blacklist time.
         * See {@link #withMultipleHostAndPort(Collection, long)} for more information about the internals.
         * <p>
         * Note that only one method that sets a {@link ConsulFailoverInterceptor} should be used when
         * constructing a Consul instance. Otherwise, only the last one is used.
         *
         * @param hostAndPort   A collection of {@link HostAndPort} that define the list of Consul agent addresses to use.
         * @param blacklistTime The timeout to blacklist a particular {@link HostAndPort} before trying to use it again.
         * @return The builder.
         * @see #withMultipleHostAndPort(Collection, long)
         */
        public Builder withMultipleHostAndPort(Collection<HostAndPort> hostAndPort, Duration blacklistTime) {
            return withMultipleHostAndPort(hostAndPort, blacklistTime.toMillis());
        }

        /**
         * Sets the list of hosts to contact if the current request target is
         * unavailable. When the call to a particular URL fails for any reason, the next {@link HostAndPort} specified
         * is used to retry the request. This will continue until all urls are exhausted.
         * <p>
         * This method sets a {@link ConsulFailoverInterceptor} using the given hosts and blacklist time.
         * See {@link #withMultipleHostAndPort(Collection, long)} for more information about the internals.
         * <p>
         * Note that only one method that sets a {@link ConsulFailoverInterceptor} should be used when
         * constructing a Consul instance. Otherwise, only the last one is used.
         *
         * @param hostAndPort       A collection of {@link HostAndPort} that define the list of Consul agent addresses to use.
         * @param blacklistTime     The timeout to blacklist a particular {@link HostAndPort} before trying to use it again.
         * @param blacklistTimeUnit The unit of {@code blacklistTime}
         * @return The builder.
         * @see #withMultipleHostAndPort(Collection, long)
         */
        public Builder withMultipleHostAndPort(Collection<HostAndPort> hostAndPort,
                                               long blacklistTime,
                                               TimeUnit blacklistTimeUnit) {
            return withMultipleHostAndPort(hostAndPort, blacklistTimeUnit.toMillis(blacklistTime));
        }

        /**
         * Constructs a failover interceptor with the given {@link ConsulFailoverStrategy}.
         * <p>
         * Note that only one method that sets a {@link ConsulFailoverInterceptor} should be used when
         * constructing a Consul instance. Otherwise, only the last one is used.
         *
         * @param strategy The strategy to use.
         * @return The builder.
         */
        public Builder withFailoverInterceptorUsingStrategy(ConsulFailoverStrategy strategy) {
            checkArgument(nonNull(strategy), "Must not provide a null strategy");
            logWarningIfConsulFailoverInterceptorAlreadySet("withFailoverInterceptorUsingStrategy");

            consulFailoverInterceptor = new ConsulFailoverInterceptor(strategy);
            ++numTimesConsulFailoverInterceptorSet;
            return this;
        }

        /**
         * Constructs a failover interceptor with the given {@link ConsulFailoverStrategy}.
         * <p>
         * Note that only one method that sets a {@link ConsulFailoverInterceptor} should be used when
         * constructing a Consul instance. Otherwise, only the last one is used.
         *
         * @param strategy The strategy to use.
         * @return The builder.
         * @deprecated replaced by {@link #withFailoverInterceptorUsingStrategy(ConsulFailoverStrategy)}
         */
        @SuppressWarnings("DeprecatedIsStillUsed")
        @Deprecated(since = "1.3.0", forRemoval = true)
        public Builder withFailoverInterceptor(ConsulFailoverStrategy strategy) {
            return withFailoverInterceptorUsingStrategy(strategy);
        }

        private void logWarningIfConsulFailoverInterceptorAlreadySet(String methodName) {
            if (numTimesConsulFailoverInterceptorSet > 0) {
                LOG.warn("A ConsulFailoverInterceptor was already present; this invocation to '{}' overrides it!" +
                                " Make sure only one method to set a failover interceptor is called.",
                        methodName);
            }
        }

        @VisibleForTesting
        int numTimesConsulFailoverInterceptorSet() {
            return numTimesConsulFailoverInterceptorSet;
        }

        /**
         * Sets the maximum number of failover attempts that the
         * {@link ConsulFailoverInterceptor}, if one is set, should use.
         *
         * @param maxFailoverAttempts the maximum number of attempts
         * @return The builder.
         * @see ConsulFailoverInterceptor#withMaxFailoverAttempts(int)
         */
        public Builder withMaxFailoverAttempts(int maxFailoverAttempts) {
            checkArgument(maxFailoverAttempts > 0, "maxFailoverAttempts must be positive");
            this.maxFailoverAttempts = maxFailoverAttempts;
            return this;
        }

        /**
        * Sets the URL from a string.
        *
        * @param url The Consul agent URL.
        * @return The builder.
        */
        public Builder withUrl(String url) {
            this.url = Urls.newUrl(url);

            return this;
        }

        /**
        * Sets the {@link SSLContext} for the client.
        *
        * @param sslContext The SSL context for HTTPS agents.
        * @return The builder.
        */
        public Builder withSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;

            return this;
        }

        /**
        * Sets the {@link X509TrustManager} for the client.
        *
        * @param trustManager The SSL trust manager for HTTPS agents.
        * @return The builder.
        */
        public Builder withTrustManager(X509TrustManager trustManager) {
            this.trustManager = trustManager;

            return this;
        }

        /**
        * Sets the {@link HostnameVerifier} for the client.
        *
        * @param hostnameVerifier The hostname verifier to use.
        * @return The builder.
        */
        public Builder withHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;

            return this;
        }

        /**
        * Sets the {@link Proxy} for the client.
        *
        * @param proxy The proxy to use.
        * @return The builder
        */
        public Builder withProxy(Proxy proxy) {
            this.proxy = proxy;

            return this;
        }

        /**
        * Connect timeout for OkHttpClient
        * @param timeoutMillis timeout values in milliseconds
        * @return The builder
        */
        public Builder withConnectTimeoutMillis(long timeoutMillis) {
            checkArgument(timeoutMillis >= 0, NEGATIVE_VALUE);
            this.networkTimeoutConfigBuilder.withConnectTimeout((int) timeoutMillis);
            return this;
        }

        /**
        * Read timeout for OkHttpClient
        * @param timeoutMillis timeout value in milliseconds
        * @return The builder
        */
        public Builder withReadTimeoutMillis(long timeoutMillis) {
            checkArgument(timeoutMillis >= 0, NEGATIVE_VALUE);
            this.networkTimeoutConfigBuilder.withReadTimeout((int) timeoutMillis);

            return this;
        }

        /**
        * Write timeout for OkHttpClient
        * @param timeoutMillis timeout value in milliseconds
        * @return The builder
        */
        public Builder withWriteTimeoutMillis(long timeoutMillis) {
            checkArgument(timeoutMillis >= 0, NEGATIVE_VALUE);
            this.networkTimeoutConfigBuilder.withWriteTimeout((int) timeoutMillis);

            return this;
        }

        /**
         * Sets the ExecutorService to be used by the internal task dispatcher.
         * <p>
         * By default, an ExecutorService is created internally.
         * In this case, it will not be customizable nor manageable by the user application.
         * It can only be shutdown by the {@link Consul#destroy()} method.
         * <p>
         * When an application needs to be able to customize the ExecutorService parameters, and/or manage its lifecycle,
         * it can provide an instance of ExecutorService to the Builder. In that case, this ExecutorService will be used instead of creating one internally.
         *
         * @param executorService The ExecutorService to be injected in the internal task dispatcher.
         * @return The builder
         */
        public Builder withExecutorService(ExecutorService executorService) {
            this.executorService = executorService;

            return this;
        }


        /**
         * Sets the ConnectionPool to be used by OkHttp Client
         * <p>
         * By default, an ConnectionPool is created internally.
         * In this case, it will not be customizable nor manageable by the user application.
         * It can only be shutdown by the {@link Consul#destroy()} method.
         * <p>
         * When an application needs to be able to customize the ConnectionPool parameters, and/or manage its lifecycle,
         * it can provide an instance of ConnectionPool to the Builder. In that case, this ConnectionPool will be used instead of creating one internally.
         *
         * @param connectionPool The ConnectionPool to be injected in the internal OkHttpClient
         * @return The builder
        */
        public Builder withConnectionPool(ConnectionPool connectionPool) {
            this.connectionPool = connectionPool;

            return this;
        }

        /**
         * Sets the configuration for the clients.
         * The configuration will fall back on the library default configuration if elements are not set.
         *
         * @param clientConfig the configuration to use.
         * @return The Builder
        */
        public Builder withClientConfiguration(ClientConfig clientConfig) {
            this.clientConfig = clientConfig;

            return this;
        }

        /**
        * Sets the event callback for the clients.
        * The callback will be called by the consul client after each event.
        *
        * @param callback the callback to call.
        * @return The Builder
        */
        public Builder withClientEventCallback(ClientEventCallback callback) {
            this.clientEventCallback = callback;

            return this;
        }

        /**
        * Constructs a new {@link Consul} client.
        *
        * @return A new Consul client.
        */
        public Consul build() {
            // if an ExecutorService is provided to the Builder, we use it, otherwise, we create one
            ExecutorService localExecutorService = this.executorService;
            if (isNull(localExecutorService)) {
                // mimics okhttp3.Dispatcher#executorService implementation, except
                // using daemon thread so shutdown is not blocked (issue #133)
                localExecutorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                        new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", true));
            }

            if (isNull(connectionPool)) {
                connectionPool = new ConnectionPool();
            }

            ClientConfig config = nonNull(clientConfig) ? clientConfig : new ClientConfig();

            var okHttpClient = createOkHttpClient(
                    this.sslContext,
                    this.trustManager,
                    this.hostnameVerifier,
                    this.proxy,
                    localExecutorService,
                    connectionPool,
                    config);
            var networkTimeoutConfig = new NetworkTimeoutConfig.Builder()
                .withConnectTimeout(okHttpClient::connectTimeoutMillis)
                .withReadTimeout(okHttpClient::readTimeoutMillis)
                .withWriteTimeout(okHttpClient::writeTimeoutMillis)
                .build();

            final Retrofit retrofit = createRetrofit(buildUrl(this.url), Jackson.MAPPER, okHttpClient);

            ClientEventCallback eventCallback = nonNull(clientEventCallback) ?
                    clientEventCallback :
                    new NoOpClientEventCallback();

            var agentClient = new AgentClient(retrofit, config, eventCallback);
            var healthClient = new HealthClient(retrofit, config, eventCallback, networkTimeoutConfig);
            var keyValueClient = new KeyValueClient(retrofit, config, eventCallback, networkTimeoutConfig);
            var catalogClient = new CatalogClient(retrofit, config, eventCallback, networkTimeoutConfig);
            var statusClient = new StatusClient(retrofit, config, eventCallback);
            var sessionClient = new SessionClient(retrofit, config, eventCallback);
            var eventClient = new EventClient(retrofit, config, eventCallback);
            var preparedQueryClient = new PreparedQueryClient(retrofit, config, eventCallback);
            var coordinateClient = new CoordinateClient(retrofit, config, eventCallback);
            var operatorClient = new OperatorClient(retrofit, config, eventCallback);
            var aclClient = new AclClient(retrofit, config, eventCallback);
            var snapshotClient = new SnapshotClient(retrofit, config, eventCallback);

            if (ping) {
                agentClient.ping();
            }
            return new Consul(agentClient,
                    healthClient,
                    keyValueClient,
                    catalogClient,
                    statusClient,
                    sessionClient,
                    eventClient,
                    preparedQueryClient,
                    coordinateClient,
                    operatorClient,
                    localExecutorService,
                    connectionPool,
                    aclClient,
                    snapshotClient,
                    okHttpClient);
        }

        private String buildUrl(URL url) {
            return url.toExternalForm().replaceAll("/$", "") + "/v1/";
        }

        private OkHttpClient createOkHttpClient(SSLContext sslContext,
                                                X509TrustManager trustManager,
                                                HostnameVerifier hostnameVerifier,
                                                Proxy proxy,
                                                ExecutorService executorService,
                                                ConnectionPool connectionPool,
                                                ClientConfig clientConfig) {

            final OkHttpClient.Builder builder = new OkHttpClient.Builder();

            if (nonNull(authInterceptor)) {
                builder.addInterceptor(authInterceptor);
            }

            if (nonNull(aclTokenInterceptor)) {
                builder.addInterceptor(aclTokenInterceptor);
            }

            if (nonNull(headerInterceptor)) {
                builder.addInterceptor(headerInterceptor);
            }

            if (nonNull(consulBookendInterceptor)) {
                builder.addInterceptor(consulBookendInterceptor);
            }

            addConsulFailoverInterceptor(builder);

            addSslSocketFactory(sslContext, trustManager, builder);

            if (nonNull(hostnameVerifier)) {
                builder.hostnameVerifier(hostnameVerifier);
            }

            if (nonNull(proxy)) {
                builder.proxy(proxy);
            }

            var networkTimeoutConfig = networkTimeoutConfigBuilder.build();
            addTimeouts(builder, networkTimeoutConfig);

            builder.addInterceptor(new TimeoutInterceptor(clientConfig.getCacheConfig()));

            var dispatcher = new Dispatcher(executorService);
            dispatcher.setMaxRequests(Integer.MAX_VALUE);
            dispatcher.setMaxRequestsPerHost(Integer.MAX_VALUE);
            builder.dispatcher(dispatcher);

            if (nonNull(connectionPool)) {
                builder.connectionPool(connectionPool);
            }

            return builder.build();
        }

        private void addConsulFailoverInterceptor(OkHttpClient.Builder builder) {
            if (isNull(consulFailoverInterceptor)) {
                return;
            }

            if (maxFailoverAttempts > 0) {
                consulFailoverInterceptor.withMaxFailoverAttempts(maxFailoverAttempts);
            }
            builder.addInterceptor(consulFailoverInterceptor);
        }

        @VisibleForTesting
        static void addSslSocketFactory(@Nullable SSLContext sslContext,
                                        @Nullable X509TrustManager trustManager,
                                        OkHttpClient.Builder builder) {

            if (isNull(sslContext)) {
                return;
            }

            var socketFactory = sslContext.getSocketFactory();
            if (nonNull(trustManager)) {
                builder.sslSocketFactory(socketFactory, trustManager);
            } else {
                builder.sslSocketFactory(socketFactory, TrustManagerUtils.getDefaultTrustManager());
            }
        }

        private static void addTimeouts(OkHttpClient.Builder builder,
                                        NetworkTimeoutConfig networkTimeoutConfig) {

            if (networkTimeoutConfig.getClientConnectTimeoutMillis() >= 0) {
                builder.connectTimeout(networkTimeoutConfig.getClientConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
            }

            if (networkTimeoutConfig.getClientReadTimeoutMillis() >= 0) {
                builder.readTimeout(networkTimeoutConfig.getClientReadTimeoutMillis(), TimeUnit.MILLISECONDS);
            }

            if (networkTimeoutConfig.getClientWriteTimeoutMillis() >= 0) {
                builder.writeTimeout(networkTimeoutConfig.getClientWriteTimeoutMillis(), TimeUnit.MILLISECONDS);
            }
        }

        private Retrofit createRetrofit(String url, ObjectMapper mapper, OkHttpClient okHttpClient) {

            final URL consulUrl = Urls.newUrl(url);

            var baseUrl = Urls.newUrl(consulUrl.getProtocol(),
                    consulUrl.getHost(),
                    consulUrl.getPort(),
                    consulUrl.getFile());

            return new Retrofit.Builder()
                    .baseUrl(baseUrl.toExternalForm())
                    .addConverterFactory(JacksonConverterFactory.create(mapper))
                    .client(okHttpClient)
                    .build();
        }

    }

    public static class NetworkTimeoutConfig {
        private final IntSupplier readTimeoutMillisSupplier;
        private final IntSupplier writeTimeoutMillisSupplier;
        private final IntSupplier connectTimeoutMillisSupplier;

        private NetworkTimeoutConfig(
                IntSupplier readTimeoutMillisSupplier,
                IntSupplier writeTimeoutMillisSupplier,
                IntSupplier connectTimeoutMillisSupplier) {
            this.readTimeoutMillisSupplier = readTimeoutMillisSupplier;
            this.writeTimeoutMillisSupplier = writeTimeoutMillisSupplier;
            this.connectTimeoutMillisSupplier = connectTimeoutMillisSupplier;
        }

        public int getClientReadTimeoutMillis() {
            return readTimeoutMillisSupplier.getAsInt();
        }
        public int getClientWriteTimeoutMillis() {
            return writeTimeoutMillisSupplier.getAsInt();
        }
        public int getClientConnectTimeoutMillis() {
            return connectTimeoutMillisSupplier.getAsInt();
        }
        public static class Builder {
            private IntSupplier readTimeoutMillisSupplier = () -> -1;
            private IntSupplier writeTimeoutMillisSupplier = () -> -1;
            private IntSupplier connectTimeoutMillisSupplier = () -> -1;

            public NetworkTimeoutConfig.Builder withReadTimeout(IntSupplier timeoutSupplier) {
                this.readTimeoutMillisSupplier = timeoutSupplier;
                return this;
            }

            public NetworkTimeoutConfig.Builder withReadTimeout(int millis) {
                return withReadTimeout(() -> millis);
            }

            public NetworkTimeoutConfig.Builder withWriteTimeout(IntSupplier timeoutSupplier) {
                this.writeTimeoutMillisSupplier = timeoutSupplier;
                return this;
            }

            public NetworkTimeoutConfig.Builder withWriteTimeout(int millis) {
                return withWriteTimeout(() -> millis);
            }

            public NetworkTimeoutConfig.Builder withConnectTimeout(IntSupplier timeoutSupplier) {
                this.connectTimeoutMillisSupplier = timeoutSupplier;
                return this;
            }

            public NetworkTimeoutConfig.Builder withConnectTimeout(int millis) {
                return withConnectTimeout(() -> millis);
            }

            public NetworkTimeoutConfig build() {
                return new NetworkTimeoutConfig(readTimeoutMillisSupplier, writeTimeoutMillisSupplier, connectTimeoutMillisSupplier);
            }
        }
    }
}
