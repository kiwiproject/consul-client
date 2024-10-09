package org.kiwiproject.consul;

import static org.kiwiproject.consul.ConsulClients.dcQuery;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.model.coordinate.Coordinate;
import org.kiwiproject.consul.model.coordinate.Datacenter;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.QueryMap;

import java.util.List;
import java.util.Map;

/**
 * HTTP Client for /v1/coordinate/ endpoints.
 *
 * @see <a href="https://developer.hashicorp.com/consul/api-docs/coordinate">The Consul API Docs</a>
 */
public class CoordinateClient extends BaseClient {

    private static final String CLIENT_NAME = "coordinate";

    private final Api api;

    /**
     * Constructs an instance of this class.
     *
     * @param retrofit The {@link Retrofit} to build a client from.
     */
    CoordinateClient(Retrofit retrofit, ClientConfig config, ClientEventCallback eventCallback) {
        super(CLIENT_NAME, config, eventCallback);
        this.api = retrofit.create(Api.class);
    }

    public List<Datacenter> getDatacenters() {
        return http.extract(api.getDatacenters());
    }

    public List<Coordinate> getNodes(String dc) {
        return http.extract(api.getNodes(dcQuery(dc)));
    }

    public List<Coordinate> getNodes() {
        return getNodes(null);
    }

    /**
     * Retrofit API interface.
     */
    interface Api {

        @GET("coordinate/datacenters")
        Call<List<Datacenter>> getDatacenters();

        @GET("coordinate/nodes")
        Call<List<Coordinate>> getNodes(@QueryMap Map<String, String> query);

    }
}
