package com.orbitz.consul.util.bookend;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ConsulBookendInterceptor implements Interceptor {

    private final ConsulBookend consulBookend;

    public ConsulBookendInterceptor(ConsulBookend consulBookend) {
        this.consulBookend = consulBookend;
    }

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        ConsulBookendContext context = new ConsulBookendContext();
        consulBookend.pre(request.url().encodedPath(), context);

        Response response = chain.proceed(request);

        consulBookend.post(response.code(), context);

        return response;
    }
}
