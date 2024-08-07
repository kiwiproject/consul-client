package org.kiwiproject.consul;

import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.catalog.CatalogDeregistration;
import org.kiwiproject.consul.model.catalog.CatalogNode;
import org.kiwiproject.consul.model.catalog.CatalogRegistration;
import org.kiwiproject.consul.model.catalog.CatalogService;
import org.kiwiproject.consul.model.health.Node;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import org.kiwiproject.consul.option.Options;
import org.kiwiproject.consul.option.QueryOptions;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

import java.util.List;
import java.util.Map;

/**
 * HTTP Client for /v1/catalog/ endpoints.
 */
public class CatalogClient extends BaseCacheableClient {

    private static final String CLIENT_NAME = "catalog";

    private final Api api;

    /**
     * Constructs an instance of this class.
     *
     * @param retrofit The {@link Retrofit} to build a client from.
     */
    CatalogClient(Retrofit retrofit, ClientConfig config, ClientEventCallback eventCallback, Consul.NetworkTimeoutConfig networkTimeoutConfig) {
        super(CLIENT_NAME, config, eventCallback, networkTimeoutConfig);
        this.api = retrofit.create(Api.class);
    }

    /**
     * Retrieves all datacenters.
     * <p>
     * GET /v1/catalog/datacenters
     *
     * @return A list of datacenter names.
     */
    public List<String> getDatacenters() {
        return getDatacenters(Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Get the list of datacenters with query options
     *
     * @param queryOptions the query options to use
     * @return a list of the datacenters
     */
    public List<String> getDatacenters(QueryOptions queryOptions) {
        return http.extract(api.getDatacenters(queryOptions.toHeaders()));
    }

    /**
     * Retrieves all nodes.
     * <p>
     * GET /v1/catalog/nodes
     *
     * @return A {@link ConsulResponse} containing a list of {@link Node} objects.
     */
    public ConsulResponse<List<Node>> getNodes() {
        return getNodes(Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Retrieves all nodes for a given datacenter with {@link QueryOptions}.
     * <p>
     * GET /v1/catalog/nodes?dc={datacenter}
     *
     * @param queryOptions The Query Options to use.
     * @return A {@link ConsulResponse} containing a list of {@link Node} objects.
     */
    public ConsulResponse<List<Node>> getNodes(QueryOptions queryOptions) {
        return http.extractConsulResponse(api.getNodes(queryOptions.toQuery(),
                queryOptions.getTag(), queryOptions.getNodeMeta(),
                queryOptions.toHeaders()));
    }

    /**
     * Asynchronously retrieves the nodes for a given datacenter with {@link QueryOptions}.
     * <p>
     * GET /v1/catalog/nodes?dc={datacenter}
     *
     * @param queryOptions The Query Options to use.
     * @param callback     Callback implemented by callee to handle results, which is a list of {@link Node} objects.
     */
    public void getNodes(QueryOptions queryOptions, ConsulResponseCallback<List<Node>> callback) {
        http.extractConsulResponse(api.getNodes(queryOptions.toQuery(), queryOptions.getTag(),
                queryOptions.getNodeMeta(), queryOptions.toHeaders()), callback);
    }

    /**
     * Retrieves all services for a given datacenter.
     * <p>
     * GET /v1/catalog/services?dc={datacenter}
     *
     * @return A {@link ConsulResponse} containing a map of service name to a list of tags.
     */
    public ConsulResponse<Map<String, List<String>>> getServices() {
        return getServices(Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Asynchronously retrieves the services for a given datacenter.
     * <p>
     * GET /v1/catalog/services?dc={datacenter}
     *
     * @param callback Callback implemented by callee to handle results; the callback is provided a map of service
     *                 name to a list of tags.
     */
    public void getServices(ConsulResponseCallback<Map<String, List<String>>> callback) {
        getServices(Options.BLANK_QUERY_OPTIONS, callback);
    }

    /**
     * Retrieves all services for a given datacenter.
     * <p>
     * GET /v1/catalog/services?dc={datacenter}
     *
     * @param queryOptions The Query Options to use.
     * @return A {@link ConsulResponse} containing a map of service name to a list of tags.
     */
    public ConsulResponse<Map<String, List<String>>> getServices(QueryOptions queryOptions) {
        return http.extractConsulResponse(api.getServices(queryOptions.toQuery(),
                queryOptions.getTag(), queryOptions.getNodeMeta(), queryOptions.toHeaders()));
    }

    /**
     * Asynchronously retrieves the services for a given datacenter.
     * <p>
     * GET /v1/catalog/services?dc={datacenter}
     *
     * @param queryOptions The Query Options to use.
     * @param callback     Callback implemented by callee to handle results, containing a map of service name to
     *                     a list of tags
     */
    public void getServices(QueryOptions queryOptions, ConsulResponseCallback<Map<String, List<String>>> callback) {
        http.extractConsulResponse(api.getServices(queryOptions.toQuery(),
                queryOptions.getTag(), queryOptions.getNodeMeta(), queryOptions.toHeaders()), callback);
    }

    /**
     * Retrieves the single service.
     * <p>
     * GET /v1/catalog/service/{service}
     *
     * @param service the name of the service to get
     * @return A {@link ConsulResponse} containing {@link CatalogService} objects.
     */
    public ConsulResponse<List<CatalogService>> getService(String service) {
        return getService(service, Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Retrieves a single service for a given datacenter with {@link QueryOptions}.
     * <p>
     * GET /v1/catalog/service/{service}?dc={datacenter}
     *
     * @param service      the name of the service to get
     * @param queryOptions The Query Options to use.
     * @return A {@link ConsulResponse} containing {@link CatalogService} objects.
     */
    public ConsulResponse<List<CatalogService>> getService(String service, QueryOptions queryOptions) {
        return http.extractConsulResponse(api.getService(service, queryOptions.toQuery(),
                queryOptions.getTag(), queryOptions.getNodeMeta(), queryOptions.toHeaders()));
    }

    /**
     * Asynchronously retrieves the single service for a given datacenter with {@link QueryOptions}.
     * <p>
     * GET /v1/catalog/service/{service}?dc={datacenter}
     *
     * @param service      the name of the service to get
     * @param queryOptions The Query Options to use.
     * @param callback     Callback implemented by callee to handle results, containing a list of {@link CatalogService} objects
     */
    public void getService(String service, QueryOptions queryOptions, ConsulResponseCallback<List<CatalogService>> callback) {
        http.extractConsulResponse(api.getService(service, queryOptions.toQuery(),
                queryOptions.getTag(), queryOptions.getNodeMeta(), queryOptions.toHeaders()), callback);
    }

    /**
     * Retrieves a single node.
     * <p>
     * GET /v1/catalog/node/{node}
     *
     * @param node the name of the node to get
     * @return A list of matching {@link CatalogService} objects.
     */
    public ConsulResponse<CatalogNode> getNode(String node) {
        return getNode(node, Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Retrieves a single node for a given datacenter with {@link QueryOptions}.
     * <p>
     * GET /v1/catalog/node/{node}?dc={datacenter}
     *
     * @param node         the name of the node to get
     * @param queryOptions The Query Options to use.
     * @return A list of matching {@link CatalogService} objects.
     */
    public ConsulResponse<CatalogNode> getNode(String node, QueryOptions queryOptions) {
        return http.extractConsulResponse(api.getNode(node, queryOptions.toQuery(),
                queryOptions.getTag(), queryOptions.getNodeMeta(), queryOptions.toHeaders()));
    }

    /**
     * Asynchronously retrieves the single node for a given datacenter with {@link QueryOptions}.
     * <p>
     * GET /v1/catalog/node/{node}?dc={datacenter}
     *
     * @param node         the name of the node to get
     * @param queryOptions The Query Options to use.
     * @param callback     Callback implemented by callee to handle results.
     */
    public void getNode(String node, QueryOptions queryOptions, ConsulResponseCallback<CatalogNode> callback) {
        http.extractConsulResponse(api.getNode(node, queryOptions.toQuery(),
                queryOptions.getTag(), queryOptions.getNodeMeta(), queryOptions.toHeaders()), callback);
    }

    /**
     * Registers a service or node.
     * <p>
     * PUT /v1/catalog/register
     *
     * @param registration A {@link CatalogRegistration}
     */
    public void register(CatalogRegistration registration) {
        register(registration, Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Registers a service or node.
     * <p>
     * PUT /v1/catalog/register
     *
     * @param registration A {@link CatalogRegistration}
     * @param options      The Query Options to use.
     */
    public void register(CatalogRegistration registration, QueryOptions options) {
        http.handle(api.register(registration, options.toQuery()));
    }

    /**
     * De-registers a service or node.
     * <p>
     * PUT /v1/catalog/deregister
     *
     * @param deregistration A {@link CatalogDeregistration}
     */
    public void deregister(CatalogDeregistration deregistration) {
        deregister(deregistration, Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * De-registers a service or node.
     * <p>
     * PUT /v1/catalog/deregister
     *
     * @param deregistration A {@link CatalogDeregistration}
     * @param options        The Query Options to use.
     */
    public void deregister(CatalogDeregistration deregistration, QueryOptions options) {
        http.handle(api.deregister(deregistration, options.toQuery()));
    }

    /**
     * Retrofit API interface.
     */
    interface Api {

        @GET("catalog/datacenters")
        Call<List<String>> getDatacenters(@HeaderMap Map<String, String> headers);

        @GET("catalog/nodes")
        Call<List<Node>> getNodes(@QueryMap Map<String, Object> query,
                                  @Query("tag") List<String> tag,
                                  @Query("node-meta") List<String> nodeMeta,
                                  @HeaderMap Map<String, String> headers);

        @GET("catalog/node/{node}")
        Call<CatalogNode> getNode(@Path("node") String node,
                                  @QueryMap Map<String, Object> query,
                                  @Query("tag") List<String> tag,
                                  @Query("node-meta") List<String> nodeMeta,
                                  @HeaderMap Map<String, String> headers);

        @GET("catalog/services")
        Call<Map<String, List<String>>> getServices(@QueryMap Map<String, Object> query,
                                                    @Query("tag") List<String> tag,
                                                    @Query("node-meta") List<String> nodeMeta,
                                                    @HeaderMap Map<String, String> headers);

        @GET("catalog/service/{service}")
        Call<List<CatalogService>> getService(@Path("service") String service,
                                              @QueryMap Map<String, Object> queryMeta,
                                              @Query("tag") List<String> tag,
                                              @Query("node-meta") List<String> nodeMeta,
                                              @HeaderMap Map<String, String> headers);

        @PUT("catalog/register")
        Call<Void> register(@Body CatalogRegistration registration, @QueryMap Map<String, Object> options);

        @PUT("catalog/deregister")
        Call<Void> deregister(@Body CatalogDeregistration deregistration, @QueryMap Map<String, Object> options);


    }
}
