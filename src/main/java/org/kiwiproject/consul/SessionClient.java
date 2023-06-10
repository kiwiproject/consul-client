package org.kiwiproject.consul;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.model.session.Session;
import org.kiwiproject.consul.model.session.SessionCreatedResponse;
import org.kiwiproject.consul.model.session.SessionInfo;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP Client for /v1/session/ endpoints.
 *
 * @see <a href="https://developer.hashicorp.com/consul/api-docs/session">The Consul API Docs</a>
 */
public class SessionClient extends BaseClient {

    private static final String CLIENT_NAME = "session";

    private final Api api;

    /**
     * Constructs an instance of this class.
     *
     * @param retrofit The {@link Retrofit} to build a client from.
     */
    SessionClient(Retrofit retrofit, ClientConfig config, ClientEventCallback eventCallback) {
        super(CLIENT_NAME, config, eventCallback);
        this.api = retrofit.create(Api.class);
    }

    /**
     * Create Session.
     * <p>
     * PUT /v1/session/create
     *
     * @param value The session to create.
     * @return ID of the newly created session .
     */
    public SessionCreatedResponse createSession(final Session value) {
        return createSession(value, null);
    }

    /**
     * Create Session.
     * <p>
     * PUT /v1/session/create
     *
     * @param value The session to create.
     * @param dc    The data center.
     * @return Response containing the session ID.
     */
    public SessionCreatedResponse createSession(final Session value, final String dc) {
        return http.extract(api.createSession(value, dcQuery(dc)));
    }

    private Map<String, String> dcQuery(String dc) {
        return nonNull(dc) ? Map.of("dc", dc) : Map.of();
    }

    public Optional<SessionInfo> renewSession(final String sessionId) {
        return renewSession(null, sessionId);
    }

    /**
     * Renews a session.
     *
     * @param dc        The datacenter.
     * @param sessionId The session ID to renew.
     * @return The {@link SessionInfo} object for the renewed session.
     */
    public Optional<SessionInfo> renewSession(final String dc, final String sessionId) {
        List<SessionInfo> sessionInfo = http.extract(api.renewSession(sessionId,
                Map.of(), dcQuery(dc)));

        return isNull(sessionInfo) || sessionInfo.isEmpty() ? Optional.empty() :
                Optional.of(sessionInfo.get(0));
    }

    /**
     * Destroys a session.
     * <p>
     * PUT /v1/session/destroy/{sessionId}
     *
     * @param sessionId The session ID to destroy.
     */
    public void destroySession(final String sessionId) {
        destroySession(sessionId, null);
    }

    /**
     * Destroys a session.
     * <p>
     * PUT /v1/session/destroy/{sessionId}
     *
     * @param sessionId The session ID to destroy.
     * @param dc        The data center.
     */
    public void destroySession(final String sessionId, final String dc) {
        http.handle(api.destroySession(sessionId, dcQuery(dc)));
    }

    /**
     * Retrieves session info.
     * <p>
     * GET /v1/session/info/{sessionId}
     *
     * @param sessionId the session ID
     * @return {@link SessionInfo}.
     */
    public Optional<SessionInfo> getSessionInfo(final String sessionId) {
        return getSessionInfo(sessionId, null);
    }

    /**
     * Retrieves session info.
     * <p>
     * GET /v1/session/info/{sessionId}
     *
     * @param sessionId the session ID
     * @param dc        Data center
     * @return {@link SessionInfo}.
     */
    public Optional<SessionInfo> getSessionInfo(final String sessionId, final String dc) {
        List<SessionInfo> sessionInfo = http.extract(api.getSessionInfo(sessionId, dcQuery(dc)));

        return isNull(sessionInfo) || sessionInfo.isEmpty() ? Optional.empty() :
                Optional.of(sessionInfo.get(0));
    }

    /**
     * Lists all sessions.
     * <p>
     * GET /v1/session/list
     *
     * @param dc The data center.
     * @return A list of available sessions.
     */
    public List<SessionInfo> listSessions(final String dc) {
        return http.extract(api.listSessions(dcQuery(dc)));
    }

    /**
     * Lists all sessions.
     * <p>
     * GET /v1/session/list
     *
     * @return A list of available sessions.
     */
    public List<SessionInfo> listSessions() {
        return listSessions(null);
    }

    /**
     * Retrofit API interface.
     */
    interface Api {

        @PUT("session/create")
        Call<SessionCreatedResponse> createSession(@Body Session value,
                                                   @QueryMap Map<String, String> query);

        @PUT("session/renew/{sessionId}")
        Call<List<SessionInfo>> renewSession(@Path("sessionId") String sessionId,
                                             @Body Map<String, String> body,
                                             @QueryMap Map<String, String> query);

        @PUT("session/destroy/{sessionId}")
        Call<Void> destroySession(@Path("sessionId") String sessionId,
                                  @QueryMap Map<String, String> query);

        @GET("session/info/{sessionId}")
        Call<List<SessionInfo>> getSessionInfo(@Path("sessionId") String sessionId,
                                               @QueryMap Map<String, String> query);

        @GET("session/list")
        Call<List<SessionInfo>> listSessions(@QueryMap Map<String, String> query);

    }
}
