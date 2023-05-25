package com.orbitz.consul.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitz.consul.ConsulException;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.monitoring.ClientEventHandler;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

class HttpTest {

    private ClientEventHandler clientEventHandler;
    private Http http;

    @BeforeEach
    void setUp() {
        clientEventHandler = mock(ClientEventHandler.class);
        http = new Http(clientEventHandler);
    }

    private <T> Function<Call<T>, T> createExtractWrapper() {
        return (call) -> http.extract(call);
    }

    private Function<Call<Void>, Void> createHandleWrapper() {
        return call -> {
            http.handle(call);
            return null;
        };
    }

    private <T> Function<Call<T>, ConsulResponse<T>> createExtractConsulResponseWrapper() {
        return (call) -> http.extractConsulResponse(call);
    }

    @Test
    void extractingBodyShouldSucceedWhenRequestSucceed() throws IOException {
        String expectedBody = "success";
        Response<String> response = Response.success(expectedBody);
        Call<String> call = createMockCallWithType(String.class);
        when(call.execute()).thenReturn(response);

        String body = http.extract(call);

        assertEquals(expectedBody, body);
    }

    @Test
    void handlingRequestShouldNotThrowWhenRequestSucceed() throws IOException {
        Response<Void> response = Response.success(204, null);
        Call<Void> call = createMockCallWithType(Void.class);
        when(call.execute()).thenReturn(response);

        http.handle(call);

        verify(call).execute();
    }

    @Test
    void extractingConsulResponseShouldSucceedWhenRequestSucceed() throws IOException {
        String expectedBody = "success";
        Response<String> response = Response.success(expectedBody);
        Call<String> call = createMockCallWithType(String.class);
        when(call.execute()).thenReturn(response);

        ConsulResponse<String> consulResponse = http.extractConsulResponse(call);

        assertEquals(expectedBody, consulResponse.getResponse());
    }

    @Test
    void extractingBodyShouldThrowWhenRequestFailed() throws IOException {
        assertThrows(ConsulException.class, () -> {
            checkForFailedRequest(createExtractWrapper());
        });
    }

    @Test
    void handlingRequestShouldThrowWhenRequestFailed() throws IOException {
        assertThrows(ConsulException.class, () -> {
            checkForFailedRequest(createHandleWrapper());
        });
    }

    @Test
    void extractingConsulResponseShouldThrowWhenRequestFailed() throws IOException {
        assertThrows(ConsulException.class, () -> {
            checkForFailedRequest(createExtractConsulResponseWrapper());
        });
    }

    @SuppressWarnings("unchecked")
    private <U, V> void checkForFailedRequest(Function<Call<U>, V> httpCall) throws IOException {
        Call<U> call = mock(Call.class);
        doThrow(new IOException("failure")).when(call).execute();

        httpCall.apply(call);
    }

    @Test
    void extractingBodyShouldThrowWhenRequestIsInvalid() throws IOException {
        assertThrows(ConsulException.class, () -> {
            checkForInvalidRequest(createExtractWrapper());
        });
    }

    @Test
    void handlingRequestShouldThrowWhenRequestIsInvalid() throws IOException {
        assertThrows(ConsulException.class, () -> {
            checkForInvalidRequest(createHandleWrapper());
        });
    }

    @Test
    void extractingConsulResponseShouldThrowWhenRequestIsInvalid() throws IOException {
        assertThrows(ConsulException.class, () -> {
            checkForInvalidRequest(createExtractConsulResponseWrapper());
        });
    }

    @SuppressWarnings("unchecked")
    private <U, V> void checkForInvalidRequest(Function<Call<U>, V> httpCall) throws IOException {
        Response<U> response = Response.error(400, ResponseBody.create("failure", MediaType.parse("")));
        Call<U> call = mock(Call.class);
        when(call.execute()).thenReturn(response);

        httpCall.apply(call);
    }

    @Test
    void extractingBodyShouldSendSuccessEventWhenRequestSucceed() throws IOException {
        checkSuccessEventIsSentWhenRequestSucceed(createExtractWrapper());
    }

    @Test
    void handlingRequestShouldSendSuccessEventWhenRequestSucceed() throws IOException {
        checkSuccessEventIsSentWhenRequestSucceed(createHandleWrapper());
    }

    @Test
    void extractingConsulResponseShouldSendSuccessEventWhenRequestSucceed() throws IOException {
        checkSuccessEventIsSentWhenRequestSucceed(createExtractConsulResponseWrapper());
    }

    @SuppressWarnings("unchecked")
    private <U, V> void checkSuccessEventIsSentWhenRequestSucceed(Function<Call<U>, V> httpCall) throws IOException {
        var expectedBody = "success";
        var response = Response.success(expectedBody);

        Call<U> call = mock(Call.class);
        when(call.execute()).thenReturn((Response<U>) response);
        when(call.request()).thenReturn(mock(Request.class));

        httpCall.apply(call);

        verify(clientEventHandler, only()).httpRequestSuccess(any(Request.class));
    }

    @Test
    void extractingBodyShouldSendFailureEventWhenRequestFailed() throws IOException {
        checkFailureEventIsSentWhenRequestFailed(createExtractWrapper());
    }

    @Test
    void handlingRequestShouldSendFailureEventWhenRequestFailed() throws IOException {
        checkFailureEventIsSentWhenRequestFailed(createHandleWrapper());
    }

    @Test
    void extractingConsulResponseShouldSendFailureEventWhenRequestFailed() throws IOException {
        checkFailureEventIsSentWhenRequestFailed(createExtractConsulResponseWrapper());
    }

    @SuppressWarnings("unchecked")
    private <U, V> void checkFailureEventIsSentWhenRequestFailed(Function<Call<U>, V> httpCall) throws IOException {
        Call<U> call = mock(Call.class);
        doThrow(new IOException("failure")).when(call).execute();
        when(call.request()).thenReturn(mock(Request.class));

        Assertions.assertThatThrownBy(() -> httpCall.apply(call)).isInstanceOf(ConsulException.class);

        verify(clientEventHandler, only()).httpRequestFailure(any(Request.class), any(Throwable.class));
    }

    @Test
    void extractingBodyShouldSendInvalidEventWhenRequestIsInvalid() throws IOException {
        checkInvalidEventIsSentWhenRequestIsInvalid(createExtractWrapper());
    }

    @Test
    void handlingRequestShouldSendInvalidEventWhenRequestIsInvalid() throws IOException {
        checkInvalidEventIsSentWhenRequestIsInvalid(createHandleWrapper());
    }

    @Test
    void extractingConsulResponseShouldSendInvalidEventWhenRequestIsInvalid() throws IOException {
        checkInvalidEventIsSentWhenRequestIsInvalid(createExtractConsulResponseWrapper());
    }

    @SuppressWarnings("unchecked")
    private <U, V> void checkInvalidEventIsSentWhenRequestIsInvalid(Function<Call<U>, V> httpCall) throws IOException {
        Response<String> response = Response.error(400, ResponseBody.create("failure", MediaType.parse("")));

        Call<U> call = mock(Call.class);
        when(call.execute()).thenReturn((Response<U>) response);
        when(call.request()).thenReturn(mock(Request.class));

        Assertions.assertThatThrownBy(() -> httpCall.apply(call)).isInstanceOf(ConsulException.class);

        verify(clientEventHandler, only()).httpRequestInvalid(any(Request.class), any(Throwable.class));
    }

    @Test
    void extractingConsulResponseAsyncShouldSucceedWhenRequestSucceed() throws IOException, InterruptedException {
        AtomicReference<ConsulResponse<String>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final ConsulResponseCallback<String> callback = new ConsulResponseCallback<String>() {
            @Override
            public void onComplete(ConsulResponse<String> consulResponse) {
                result.set(consulResponse);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) { }
        };
        Call<String> call = createMockCallWithType(String.class);
        Request request = new Request.Builder().url("http://localhost:8500/this/endpoint").build();
        when(call.request()).thenReturn(request);
        Callback<String> callCallback = http.createCallback(call, callback);
        String expectedBody = "success";

        Response<String> response = Response.success(expectedBody);
        callCallback.onResponse(call, response);
        latch.await(1, TimeUnit.SECONDS);

        assertEquals(expectedBody, result.get().getResponse());
        verify(clientEventHandler, only()).httpRequestSuccess(any(Request.class));
    }

    @Test
    void extractingConsulResponseAsyncShouldFailWhenRequestIsInvalid() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final ConsulResponseCallback<String> callback = new ConsulResponseCallback<String>() {
            @Override
            public void onComplete(ConsulResponse<String> consulResponse) {}

            @Override
            public void onFailure(Throwable throwable) {
                latch.countDown();
            }
        };
        Call<String> call = createMockCallWithType(String.class);
        Request request = new Request.Builder().url("http://localhost:8500/this/endpoint").build();
        when(call.request()).thenReturn(request);
        Callback<String> callCallback = http.createCallback(call, callback);

        Response<String> response = Response.error(400, ResponseBody.create("failure", MediaType.parse("")));
        callCallback.onResponse(call, response);
        latch.await(1, TimeUnit.SECONDS);

        verify(clientEventHandler, only()).httpRequestInvalid(any(Request.class), any(Throwable.class));
    }

    @Test
    void extractingConsulResponseAsyncShouldFailWhenRequestFailed() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final ConsulResponseCallback<String> callback = new ConsulResponseCallback<String>() {
            @Override
            public void onComplete(ConsulResponse<String> consulResponse) {}

            @Override
            public void onFailure(Throwable throwable) {
                latch.countDown();
            }
        };
        Call<String> call = createMockCallWithType(String.class);
        when(call.request()).thenReturn(mock(Request.class));

        Callback<String> callCallback = http.createCallback(call, callback);

        callCallback.onFailure(call, new RuntimeException("the request failed"));

        latch.await(1, TimeUnit.SECONDS);
        verify(clientEventHandler, only()).httpRequestFailure(any(Request.class), any(Throwable.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> Call<T> createMockCallWithType(Class<T> resultType) {
        return mock(Call.class);
    }

    @Test
    void consulResponseShouldHaveResponseAndDefaultValuesIfNoHeader() {
        String responseMessage = "success";
        ConsulResponse<String> expectedConsulResponse = new ConsulResponse<>(responseMessage, 0, false, BigInteger.ZERO, null, null);

        Response<String> response = Response.success(responseMessage);
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertEquals(expectedConsulResponse, consulResponse);
    }

    @Test
    void consulResponseShouldHaveIndexIfPresentInHeader() {
        Response<String> response = Response.success("", Headers.of("X-Consul-Index", "10"));
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertEquals(BigInteger.TEN, consulResponse.getIndex());
    }

    @Test
    void consulResponseShouldHaveLastContactIfPresentInHeader() {
        Response<String> response = Response.success("", Headers.of("X-Consul-Lastcontact", "2"));
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertEquals(2L, consulResponse.getLastContact());
    }

    @Test
    void consulResponseShouldHaveKnownLeaderIfPresentInHeader() {
        Response<String> response = Response.success("", Headers.of("X-Consul-Knownleader", "true"));
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertEquals(true, consulResponse.isKnownLeader());
    }
}
