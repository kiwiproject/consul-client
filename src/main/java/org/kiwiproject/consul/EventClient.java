package org.kiwiproject.consul;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.async.EventResponseCallback;
import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.EventResponse;
import org.kiwiproject.consul.model.ImmutableEventResponse;
import org.kiwiproject.consul.model.event.Event;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import org.kiwiproject.consul.option.EventOptions;
import org.kiwiproject.consul.option.Options;
import org.kiwiproject.consul.option.QueryOptions;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

import java.util.List;
import java.util.Map;

/**
 * HTTP Client for /v1/event/ endpoints.
 *
 * @see <a href="https://developer.hashicorp.com/consul/api-docs/event">The Consul API Docs</a>
 */
public class EventClient extends BaseClient {

    private static final String CLIENT_NAME = "event";

    private final Api api;

    /**
     * Constructs an instance of this class.
     *
     * @param retrofit The {@link Retrofit} to build a client from.
     */
    EventClient(Retrofit retrofit, ClientConfig config, ClientEventCallback eventCallback) {
        super(CLIENT_NAME, config, eventCallback);
        this.api = retrofit.create(Api.class);
    }

    /**
     * Fires a Consul event.
     * <p>
     * PUT /v1/event/fire/{name}
     *
     * @param name         The name of the event.
     * @param eventOptions The event specific options to use.
     * @param payload      Optional string payload.
     * @return The newly created {@link Event}.
     */
    public Event fireEvent(String name, EventOptions eventOptions, String payload) {
        return http.extract(api.fireEvent(name,
                RequestBody.create(payload, MediaType.parse("text/plain")),
                eventOptions.toQuery()));
    }

    /**
     * Fires a Consul event.
     * <p>
     * PUT /v1/event/fire/{name}
     *
     * @param name The name of the event.
     * @return The newly created {@link Event}.
     */
    public Event fireEvent(String name) {
        return fireEvent(name, Options.BLANK_EVENT_OPTIONS);
    }

    /**
     * Fires a Consul event.
     * <p>
     * PUT /v1/event/fire/{name}
     *
     * @param name The name of the event.
     * @param eventOptions The event specific options to use.
     * @return The newly created {@link Event}.
     */
    public Event fireEvent(String name, EventOptions eventOptions) {
        return http.extract(api.fireEvent(name, eventOptions.toQuery()));
    }

    /**
     * Fires a Consul event.
     * <p>
     * PUT /v1/event/fire/{name}
     *
     * @param name The name of the event.
     * @param payload Optional string payload.
     * @return The newly created {@link Event}.
     */
    public Event fireEvent(String name, String payload) {
        return fireEvent(name, Options.BLANK_EVENT_OPTIONS, payload);
    }

    /**
     * Lists events for the Consul agent.
     * <p>
     * GET /v1/event/list?name={name}
     *
     * @param name Event name to filter.
     * @param queryOptions The query options to use.
     * @return A {@link ConsulResponse} object containing
     *  a list of {@link Event} objects.
     */
    public EventResponse listEvents(String name, QueryOptions queryOptions) {
        final Map<String, Object> query = queryOptions.toQuery();
        if (StringUtils.isNotEmpty(name)) {
            query.put("name", name);
        }

        ConsulResponse<List<Event>> response = http.extractConsulResponse(api.listEvents(query));
        return ImmutableEventResponse.of(response.getResponse(), response.getIndex());
    }

    /**
     * Lists events for the Consul agent.
     * <p>
     * GET /v1/event/list?name={name}
     *
     * @param name Event name to filter.
     * @return A {@link ConsulResponse} object containing
     *  a list of {@link Event} objects.
     */
    public EventResponse listEvents(String name) {
        return listEvents(name, Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Lists events for the Consul agent.
     * <p>
     * GET /v1/event/list
     *
     * @param queryOptions The query options to use.
     * @return A {@link ConsulResponse} object containing
     *  a list of {@link Event} objects.
     */
    public EventResponse listEvents(QueryOptions queryOptions) {
        return listEvents(null, queryOptions);
    }

    /**
     * Lists events for the Consul agent.
     * <p>
     * GET /v1/event/list
     *
     * @return A {@link ConsulResponse} object containing
     *  a list of {@link Event} objects.
     */
    public EventResponse listEvents() {
        return listEvents(null, Options.BLANK_QUERY_OPTIONS);
    }

    /**
     * Asynchronously lists events for the Consul agent.
     * <p>
     * GET /v1/event/list?name={name}
     *
     * @param name Event name to filter.
     * @param queryOptions The query options to use.
     * @param callback The callback to asynchronously process the result.
     */
    public void listEvents(String name, QueryOptions queryOptions, EventResponseCallback callback) {
        final Map<String, Object> query = queryOptions.toQuery();
        if (StringUtils.isNotEmpty(name)) {
            query.put("name", name);
        }

        http.extractConsulResponse(api.listEvents(query), createConsulResponseCallbackWrapper(callback));
    }

    private ConsulResponseCallback<List<Event>> createConsulResponseCallbackWrapper(EventResponseCallback callback) {
        return new ConsulResponseCallback<>() {
            @Override
            public void onComplete(ConsulResponse<List<Event>> response) {
                callback.onComplete(ImmutableEventResponse.of(response.getResponse(), response.getIndex()));
            }

            @Override
            public void onFailure(Throwable throwable) {
                callback.onFailure(throwable);
            }
        };
    }

    /**
     * Asynchronously lists events for the Consul agent.
     * <p>
     * GET /v1/event/list
     *
     * @param queryOptions The query options to use.
     * @param callback The callback to asynchronously process the result.
     */
    public void listEvents(QueryOptions queryOptions, EventResponseCallback callback) {
        listEvents(null, queryOptions, callback);
    }

    /**
     * Asynchronously lists events for the Consul agent.
     * <p>
     * GET /v1/event/list
     *
     * @param callback The callback to asynchronously process the result.
     */
    public void listEvents(EventResponseCallback callback) {
        listEvents(null, Options.BLANK_QUERY_OPTIONS, callback);
    }

    /**
     * Retrofit API interface.
     */
    interface Api {

        @PUT("event/fire/{name}")
        Call<Event> fireEvent(@Path("name") String name,
                              @Body RequestBody payload,
                              @QueryMap Map<String, Object> query);

        @PUT("event/fire/{name}")
        Call<Event> fireEvent(@Path("name") String name,
                              @QueryMap Map<String, Object> query);

        @GET("event/list")
        Call<List<Event>> listEvents(@QueryMap Map<String, Object> query);
    }
}
