package com.orbitz.consul;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

import java.io.IOException;

class ConsulExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        var ex = new ConsulException("oop");

        assertThat(ex.getMessage()).isEqualTo("oop");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.getCode()).isZero();
        assertThat(ex.hasCode()).isFalse();
    }

    @Test
    void shouldCreateWithMessageAndThrowable() {
        var cause = new IOException("I/O error");
        var ex = new ConsulException("oop", cause);

        assertThat(ex.getMessage()).isEqualTo("oop");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getCode()).isZero();
        assertThat(ex.hasCode()).isFalse();
    }

    @Test
    void shouldCreateWithCodeAndResponse() {
        var response = Response.error(404,
                ResponseBody.create("Not Found", MediaType.get("application/json")));
        var ex = new ConsulException(404, response);

        assertThat(ex.getMessage()).isEqualTo("Consul request failed with status [404]: Not Found");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.getCode()).isEqualTo(404);
        assertThat(ex.hasCode()).isTrue();
    }

    // This is testing the code "as-is" when we imported into kiwiproject. Looking at the OkHttp code,
    // I'm not sure if this can really happen, unless someone does what this test is doing and supplies
    // a response with a success status code (2xx).
    @Test
    void shouldCreateWithCodeAndResponse_ThatContainsNullResponseErrorBody() {
        var response = Response.success(206, "Partial Content");
        var ex = new ConsulException(206, response);

        assertThat(ex.getMessage()).startsWith("Consul request failed with status [206]:");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.getCode()).isEqualTo(206);
        assertThat(ex.hasCode()).isTrue();
    }

    @Test
    void shouldCreateWithThrowable() {
        var cause = new IOException("I/O error");
        var ex = new ConsulException(cause);

        assertThat(ex.getMessage()).isEqualTo("Consul request failed");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getCode()).isZero();
        assertThat(ex.hasCode()).isFalse();
    }
}
