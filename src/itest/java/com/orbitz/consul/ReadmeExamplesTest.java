package com.orbitz.consul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.orbitz.consul.cache.KVCache;
import com.orbitz.consul.cache.ServiceHealthCache;
import com.orbitz.consul.cache.ServiceHealthKey;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.model.kv.Value;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Examples for "README.md" file.
 * Update the "README.md" file after any change.
 */
class ReadmeExamplesTest extends BaseIntegrationTest {

    @Test
    @Disabled("This must be ignored because BaseIntegrationTest starts Consul on a random port")
    @SuppressWarnings("all")
    void example1() {
        Consul client = Consul.builder().build(); // connect to Consul on localhost on the default port 8500
    }

    @Test
    void example2() throws NotRegisteredException {
        AgentClient agentClient = client.agentClient();

        String serviceId = "1";
        Registration service = ImmutableRegistration.builder()
                .id(serviceId)
                .name("myService")
                .port(8080)
                .check(Registration.RegCheck.ttl(3L)) // registers with a TTL of 3 seconds
                .tags(List.of("tag1"))
                .meta(Map.of("version", "1.0"))
                .build();

        agentClient.register(service);

        // Check in with Consul (serviceId required only).
        // Client will prepend "service:" for service level checks.
        // Note that you need to continually check in before the TTL expires, otherwise your service's state will be marked as "critical".
        agentClient.pass(serviceId);
    }

    @Test
    void example3() {
        HealthClient healthClient = client.healthClient();

        // Discover only "passing" nodes
        List<ServiceHealth> nodes = healthClient.getHealthyServiceInstances("DataService").getResponse();

        assertNotNull(nodes);
    }

    @Test
    void example4() {
        KeyValueClient kvClient = client.keyValueClient();

        kvClient.putValue("foo", "bar");
        String value = kvClient.getValueAsString("foo").get(); // bar

        assertEquals("bar", value);
    }

    @Test
    void example5() {
        final KeyValueClient kvClient = client.keyValueClient();

        kvClient.putValue("foo", "bar");

        KVCache cache = KVCache.newCache(kvClient, "foo");
        cache.addListener(newValues -> {
            // Cache notifies all paths with "foo" the root path
            // If you want to watch only "foo" value, you must filter other paths
            Optional<Value> newValue = newValues.values().stream()
                    .filter(value -> value.getKey().equals("foo"))
                    .findAny();

            newValue.ifPresent(value -> {
                // Values are encoded in key/value store, decode it if needed
                Optional<String> decodedValue = newValue.get().getValueAsString();
                decodedValue.ifPresent(v -> System.out.println(String.format("Value is: %s", v))); //prints "bar"
            });
        });
        cache.start();
        // ...
        cache.stop();
    }

    @Test
    void example6() {
        HealthClient healthClient = client.healthClient();
        String serviceName = "my-service";

        ServiceHealthCache svHealth = ServiceHealthCache.newCache(healthClient, serviceName);
        svHealth.addListener((Map<ServiceHealthKey, ServiceHealth> newValues) -> {
            // do something with updated server map
        });
        svHealth.start();
        // ...
        svHealth.stop();
    }

    @Test
    void example7() {
        StatusClient statusClient = client.statusClient();
        statusClient.getPeers().forEach(System.out::println);
    }

    @Test
    void example8() {
        StatusClient statusClient = client.statusClient();
        System.out.println(statusClient.getLeader()); // 127.0.0.1:8300
    }
}
