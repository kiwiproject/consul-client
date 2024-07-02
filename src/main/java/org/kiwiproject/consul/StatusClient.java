package org.kiwiproject.consul;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import org.kiwiproject.consul.option.Options;
import org.kiwiproject.consul.option.QueryOptions;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.QueryMap;

import java.util.List;
import java.util.Map;

public class StatusClient extends BaseClient {

    private static final String CLIENT_NAME = "status";

    private final Api api;

    /**
     * Constructs an instance of this class.
     *
     * @param retrofit The {@link Retrofit} to build a client from.
     */
    StatusClient(Retrofit retrofit, ClientConfig config, ClientEventCallback eventCallback) {
        super(CLIENT_NAME, config, eventCallback);
        this.api = retrofit.create(Api.class);
    }

    /**
     * Retrieves the host/port of the Consul leader.
     * <p>
     * GET /v1/status/leader
     *
     * @return The host/port of the leader.
     */
    public String getLeader() {
        return getLeader(Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Retrieves the host/port of the Consul leader.
     * <p>
     * GET /v1/status/leader
     *
     * @param queryOptions The Query Options to use.
     * @return The host/port of the leader.
     */
    public String getLeader(QueryOptions queryOptions) {
        return http.extract(api.getLeader(queryOptions.toQuery())).replace("\"", "").trim();
    }

    /**
     * Retrieves a list of host/ports for raft peers.
     * <p>
     * GET /v1/status/peers
     *
     * @return List of host/ports for raft peers.
     */
    public List<String> getPeers() {
        return getPeers(Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Retrieves a list of host/ports for raft peers.
     * <p>
     * GET /v1/status/peers
     *
     * @param queryOptions The Query Options to use.
     * @return List of host/ports for raft peers.
     */
    public List<String> getPeers(QueryOptions queryOptions) {
        return http.extract(api.getPeers(queryOptions.toQuery()));
    }

    /**
     * Retrofit API interface.
     */
    interface Api {

        @GET("status/leader")
        Call<String> getLeader(@QueryMap Map<String, Object> options);

        @GET("status/peers")
        Call<List<String>> getPeers(@QueryMap Map<String, Object> options);
    }
}
