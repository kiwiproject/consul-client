package com.orbitz.consul;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ConsulExceptionTest {

    @Test
    public void shouldCreateWithMessage() {
        var ex = new ConsulException("oop");

        assertThat(ex.getMessage(), is("oop"));
        assertThat(ex.getCause(), is(nullValue()));
        assertThat(ex.getCode(), is(0));
        assertThat(ex.hasCode(), is(false));
    }

    @Test
    public void shouldCreateWithMessageAndThrowable() {
        var cause = new IOException("I/O error");
        var ex = new ConsulException("oop", cause);

        assertThat(ex.getMessage(), is("oop"));
        assertThat(ex.getCause(), is(cause));
        assertThat(ex.getCode(), is(0));
        assertThat(ex.hasCode(), is(false));
    }

    @Test
    public void shouldCreateWithCodeAndResponse() {
        var response = Response.error(404,
                ResponseBody.create("Not Found", MediaType.get("application/json")));
        var ex = new ConsulException(404, response);

        assertThat(ex.getMessage(), is("Consul request failed with status [404]: Not Found"));
        assertThat(ex.getCause(), is(nullValue()));
        assertThat(ex.getCode(), is(404));
        assertThat(ex.hasCode(), is(true));
    }

    // This is testing the code "as-is" when we imported into kiwiproject. Looking at the OkHttp code,
    // I'm not sure this can really happen, unless someone does what this test is doing and supplies
    // a response with a success status code (2xx).
    @Test
    public void shouldCreateWithCodeAndResponse_ThatContainsNullResponseErrorBody() {
        var response = Response.success(206, "Partial Content");
        var ex = new ConsulException(206, response);

        assertThat(ex.getMessage(), startsWith("Consul request failed with status [206]:"));
        assertThat(ex.getCause(), is(nullValue()));
        assertThat(ex.getCode(), is(206));
        assertThat(ex.hasCode(), is(true));
    }

    @Test
    public void shouldCreateWithThrowable() {
        var cause = new IOException("I/O error");
        var ex = new ConsulException(cause);

        assertThat(ex.getMessage(), is("Consul request failed"));
        assertThat(ex.getCause(), is(cause));
        assertThat(ex.getCode(), is(0));
        assertThat(ex.hasCode(), is(false));
    }
}
