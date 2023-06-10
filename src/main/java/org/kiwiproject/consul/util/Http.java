package org.kiwiproject.consul.util;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import okhttp3.Headers;
import org.apache.commons.lang3.math.NumberUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.async.Callback;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.monitoring.ClientEventHandler;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigInteger;

public class Http {

    private final ClientEventHandler eventHandler;

    public Http(ClientEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    private static boolean isSuccessful(Response<?> response, Integer... okCodes) {
        return response.isSuccessful() || Sets.newHashSet(okCodes).contains(response.code());
    }

    public <T> T extract(Call<T> call, Integer... okCodes) {
        Response<T> response = executeCall(call);
        ensureResponseSuccessful(call, response, okCodes);
        return response.body();
    }

    public void handle(Call<Void> call, Integer... okCodes) {
        Response<Void> response = executeCall(call);
        ensureResponseSuccessful(call, response, okCodes);
    }

    public <T> ConsulResponse<T> extractConsulResponse(Call<T> call, Integer... okCodes) {
        Response<T> response = executeCall(call);
        ensureResponseSuccessful(call, response, okCodes);
        return consulResponse(response);
    }

    private <T> Response<T> executeCall(Call<T> call) {
        try {
            return call.execute();
        } catch (IOException e) {
            eventHandler.httpRequestFailure(call.request(), e);
            throw new ConsulException(e);
        }
    }

    private <T> void ensureResponseSuccessful(Call<T> call, Response<T> response, Integer... okCodes) {
        if(isSuccessful(response, okCodes)) {
            eventHandler.httpRequestSuccess(call.request());
        } else {
            ConsulException exception = new ConsulException(response.code(), response);
            eventHandler.httpRequestInvalid(call.request(), exception);
            throw exception;
        }
    }

    public <T> void extractConsulResponse(Call<T> call, ConsulResponseCallback<T> callback, Integer... okCodes) {
        var retrofitCallback = createRetrofitCallback(callback, okCodes);
        call.enqueue(retrofitCallback);
    }

    @VisibleForTesting
    <T> retrofit2.Callback<T> createRetrofitCallback(ConsulResponseCallback<T> callback, Integer... okCodes) {
        return new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                if (isSuccessful(response, okCodes)) {
                    eventHandler.httpRequestSuccess(call.request());
                    callback.onComplete(consulResponse(response));
                } else {
                    ConsulException exception = new ConsulException(response.code(), response);
                    eventHandler.httpRequestInvalid(call.request(), exception);
                    callback.onFailure(exception);
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                eventHandler.httpRequestFailure(call.request(), t);
                callback.onFailure(t);
            }
        };
    }

    public <T> void extractBasicResponse(Call<T> call, final Callback<T> callback,
                                                final Integer... okCodes) {
        extractConsulResponse(call, createConsulResponseCallbackWrapper(callback), okCodes);
    }

    private <T> ConsulResponseCallback<T> createConsulResponseCallbackWrapper(Callback<T> callback) {
        return new ConsulResponseCallback<>() {
            @Override
            public void onComplete(ConsulResponse<T> consulResponse) {
                callback.onResponse(consulResponse.getResponse());
            }

            @Override
            public void onFailure(Throwable throwable) {
                callback.onFailure(throwable);
            }
        };
    }

    @VisibleForTesting
    static <T> ConsulResponse<T> consulResponse(Response<T> response) {
        Headers headers = response.headers();
        String indexHeaderValue = headers.get("X-Consul-Index");
        String lastContactHeaderValue = headers.get("X-Consul-Lastcontact");
        String knownLeaderHeaderValue = headers.get("X-Consul-Knownleader");

        BigInteger index = isNull(indexHeaderValue) ? BigInteger.ZERO : new BigInteger(indexHeaderValue);
        long lastContact = isNull(lastContactHeaderValue) ? 0 : NumberUtils.toLong(lastContactHeaderValue);
        boolean knownLeader = nonNull(knownLeaderHeaderValue) && Boolean.parseBoolean(knownLeaderHeaderValue);
        return new ConsulResponse<>(response.body(), lastContact, knownLeader, index,
                                    headers.get("X-Cache"), headers.get("Age"));
    }
}
