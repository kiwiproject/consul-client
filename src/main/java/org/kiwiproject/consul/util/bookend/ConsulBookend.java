package org.kiwiproject.consul.util.bookend;

public interface ConsulBookend {

    void pre(String url, ConsulBookendContext context);
    void post(int code, ConsulBookendContext context);
}
