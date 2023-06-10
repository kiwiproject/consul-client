package org.kiwiproject.consul.async;

public interface Callback<T> {

    void onResponse(T result);

    void onFailure(Throwable t);
}
