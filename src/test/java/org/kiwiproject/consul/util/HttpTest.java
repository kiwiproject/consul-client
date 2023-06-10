package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.monitoring.ClientEventHandler;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
        var expectedBody = "success";
        Response<String> response = Response.success(expectedBody);
        Call<String> call = createMockCallWithType(String.class);
        when(call.execute()).thenReturn(response);

        String body = http.extract(call);

        assertThat(body).isEqualTo(expectedBody);
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
        var expectedBody = "success";
        Response<String> response = Response.success(expectedBody);
        Call<String> call = createMockCallWithType(String.class);
        when(call.execute()).thenReturn(response);

        ConsulResponse<String> consulResponse = http.extractConsulResponse(call);

        assertThat(consulResponse.getResponse()).isEqualTo(expectedBody);
    }

    @Test
    void extractingBodyShouldThrowWhenRequestFailed() {
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() ->
                checkForFailedRequest(createExtractWrapper()));
    }

    @Test
    void handlingRequestShouldThrowWhenRequestFailed() {
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() ->
                checkForFailedRequest(createHandleWrapper()));
    }

    @Test
    void extractingConsulResponseShouldThrowWhenRequestFailed() {
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() ->
                checkForFailedRequest(createExtractConsulResponseWrapper()));
    }

    @SuppressWarnings("unchecked")
    private <U, V> void checkForFailedRequest(Function<Call<U>, V> httpCall) throws IOException {
        Call<U> call = mock(Call.class);
        doThrow(new IOException("failure")).when(call).execute();

        httpCall.apply(call);
    }

    @Test
    void extractingBodyShouldThrowWhenRequestIsInvalid() {
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() ->
                checkForInvalidRequest(createExtractWrapper()));
    }

    @Test
    void handlingRequestShouldThrowWhenRequestIsInvalid() {
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() ->
                checkForInvalidRequest(createHandleWrapper()));
    }

    @Test
    void extractingConsulResponseShouldThrowWhenRequestIsInvalid() {
        assertThatExceptionOfType(ConsulException.class).isThrownBy(() ->
                checkForInvalidRequest(createExtractConsulResponseWrapper()));
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

        assertThatThrownBy(() -> httpCall.apply(call)).isInstanceOf(ConsulException.class);

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

        assertThatThrownBy(() -> httpCall.apply(call)).isInstanceOf(ConsulException.class);

        verify(clientEventHandler, only()).httpRequestInvalid(any(Request.class), any(Throwable.class));
    }

    @Test
    void extractingConsulResponseAsyncShouldSucceedWhenRequestSucceed() throws InterruptedException {
        AtomicReference<ConsulResponse<String>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        final ConsulResponseCallback<String> callback = new ConsulResponseCallback<>() {
            @Override
            public void onComplete(ConsulResponse<String> consulResponse) {
                result.set(consulResponse);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        };
        var call = createMockCallWithType(String.class);
        var request = new Request.Builder().url("http://localhost:8500/this/endpoint").build();
        when(call.request()).thenReturn(request);
        Callback<String> retrofitCallback = http.createRetrofitCallback(callback);
        var expectedBody = "success";

        Response<String> response = Response.success(expectedBody);
        retrofitCallback.onResponse(call, response);
        var countReachedZero = latch.await(1, TimeUnit.SECONDS);
        assertThat(countReachedZero).isTrue();

        assertThat(result.get().getResponse()).isEqualTo(expectedBody);
        verify(clientEventHandler, only()).httpRequestSuccess(any(Request.class));
    }

    @Test
    void extractingConsulResponseAsyncShouldFailWhenRequestIsInvalid() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var callback = new ConsulResponseCallback<String>() {
            @Override
            public void onComplete(ConsulResponse<String> consulResponse) {
            }

            @Override
            public void onFailure(Throwable throwable) {
                latch.countDown();
            }
        };
        var call = createMockCallWithType(String.class);
        var request = new Request.Builder().url("http://localhost:8500/this/endpoint").build();
        when(call.request()).thenReturn(request);
        Callback<String> retrofitCallback = http.createRetrofitCallback(callback);

        Response<String> response = Response.error(400, ResponseBody.create("failure", MediaType.parse("")));
        retrofitCallback.onResponse(call, response);
        var countReachedZero = latch.await(1, TimeUnit.SECONDS);
        assertThat(countReachedZero).isTrue();

        verify(clientEventHandler, only()).httpRequestInvalid(any(Request.class), any(Throwable.class));
    }

    @Test
    void extractingConsulResponseAsyncShouldFailWhenRequestFailed() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var callback = new ConsulResponseCallback<String>() {
            @Override
            public void onComplete(ConsulResponse<String> consulResponse) {
            }

            @Override
            public void onFailure(Throwable throwable) {
                latch.countDown();
            }
        };
        var call = createMockCallWithType(String.class);
        when(call.request()).thenReturn(mock(Request.class));

        Callback<String> retrofitCallback = http.createRetrofitCallback(callback);

        retrofitCallback.onFailure(call, new RuntimeException("the request failed"));

        var countReachedZero = latch.await(1, TimeUnit.SECONDS);
        assertThat(countReachedZero).isTrue();

        verify(clientEventHandler, only()).httpRequestFailure(any(Request.class), any(Throwable.class));
    }

    @SuppressWarnings({ "unchecked", "unused" })
    private static <T> Call<T> createMockCallWithType(Class<T> resultType) {
        return mock(Call.class);
    }

    @Test
    void consulResponseShouldHaveResponseAndDefaultValuesIfNoHeader() {
        var responseMessage = "success";
        ConsulResponse<String> expectedConsulResponse = new ConsulResponse<>(responseMessage, 0, false, BigInteger.ZERO, null, null);

        Response<String> response = Response.success(responseMessage);
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertThat(consulResponse).isEqualTo(expectedConsulResponse);
    }

    @Test
    void consulResponseShouldHaveIndexIfPresentInHeader() {
        Response<String> response = Response.success("", Headers.of("X-Consul-Index", "10"));
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertThat(consulResponse.getIndex()).isEqualTo(BigInteger.TEN);
    }

    @Test
    void consulResponseShouldHaveLastContactIfPresentInHeader() {
        Response<String> response = Response.success("", Headers.of("X-Consul-Lastcontact", "2"));
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertThat(consulResponse.getLastContact()).isEqualTo(2L);
    }

    @Test
    void consulResponseShouldHaveKnownLeaderIfPresentInHeader() {
        Response<String> response = Response.success("", Headers.of("X-Consul-Knownleader", "true"));
        ConsulResponse<String> consulResponse = Http.consulResponse(response);

        assertThat(consulResponse.isKnownLeader()).isTrue();
    }
}
