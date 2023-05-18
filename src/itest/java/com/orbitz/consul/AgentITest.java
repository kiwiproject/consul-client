package com.orbitz.consul;

import com.google.common.collect.ImmutableList;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.agent.Agent;
import com.orbitz.consul.model.agent.FullService;
import com.orbitz.consul.model.agent.ImmutableFullService;
import com.orbitz.consul.model.agent.ImmutableRegCheck;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.catalog.ImmutableServiceWeights;
import com.orbitz.consul.model.health.HealthCheck;
import com.orbitz.consul.model.health.ImmutableService;
import com.orbitz.consul.model.health.Service;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.ImmutableQueryParameterOptions;
import com.orbitz.consul.option.QueryOptions;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class AgentITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = Collections.emptyList();
    private static final Map<String, String> NO_META = Collections.emptyMap();

    @Test
    public void shouldRetrieveAgentInformation() {
        Agent agent = client.agentClient().getAgent();

        org.junit.Assume.assumeTrue(agent.getDebugConfig() != null);

        assertNotNull(agent);
        assertNotNull(agent.getConfig());
        final List<?> clientAddrs = (List<?>) agent.getDebugConfig().get("ClientAddrs");
        assertNotNull(clientAddrs.get(0));

        // maybe we should not make any assertion on the actual value of the client address
        // as like when we run consul in a docker container we would have "0.0.0.0"
        assertThat(clientAddrs.get(0), anyOf(is("127.0.0.1"), is("0.0.0.0")));
    }

    @Test
    public void shouldRegisterTtlCheck() throws UnknownHostException, InterruptedException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 10000L, serviceName, serviceId, NO_TAGS, NO_META);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldRegisterHttpCheck() throws UnknownHostException, InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, new URL("http://localhost:1337/health"), 1000L, serviceName, serviceId, NO_TAGS, NO_META);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldRegisterGrpcCheck() throws UnknownHostException, InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        Registration registration = ImmutableRegistration.builder()
                .name(serviceName)
                .id(serviceId)
                .port(12345)
                .addChecks(ImmutableRegCheck.builder()
                    .grpc("localhost:12345")
                    .interval("10s")
                    .build())
                .build();
        client.agentClient().register(registration);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldRegisterCheckWithId() throws UnknownHostException, InterruptedException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String checkId = UUID.randomUUID().toString();

        Registration registration = ImmutableRegistration.builder()
                .name(serviceName)
                .id(serviceId)
                .port(8085)
                .addChecks(ImmutableRegCheck.builder()
                        .id(checkId)
                        .ttl("10s")
                        .build())
                .build();

        client.agentClient().register(registration);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
                assertTrue(health.getChecks().stream().anyMatch(check -> check.getCheckId().equals(checkId)));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldRegisterCheckWithName() throws UnknownHostException, InterruptedException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String checkName = UUID.randomUUID().toString();

        Registration registration = ImmutableRegistration.builder()
                .name(serviceName)
                .id(serviceId)
                .port(9142)
                .addChecks(ImmutableRegCheck.builder()
                        .name(checkName)
                        .ttl("10s")
                        .build())
                .build();

        client.agentClient().register(registration);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
                assertTrue(health.getChecks().stream().anyMatch(check -> check.getName().equals(checkName)));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldRegisterMultipleChecks() throws UnknownHostException, InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        List<Registration.RegCheck> regChecks = ImmutableList.of(
                Registration.RegCheck.args(Collections.singletonList("/usr/bin/echo \"sup\""), 10, 1, "Custom description."),
                Registration.RegCheck.http("http://localhost:8080/health", 10, 1, "Custom description."));

        client.agentClient().register(8080, regChecks, serviceName, serviceId, NO_TAGS, NO_META);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(3));
            }
        }

        assertTrue(found);
    }

    // This is apparently valid
    // to register a single "Check"
    // and multiple "Checks" in one call
    @Test
    public void shouldRegisterMultipleChecks2() throws UnknownHostException, InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        Registration.RegCheck single= Registration.RegCheck.args(Collections.singletonList("/usr/bin/echo \"sup\""), 10);

        List<Registration.RegCheck> regChecks = ImmutableList.of(
                Registration.RegCheck.http("http://localhost:8080/health", 10));

        Registration reg = ImmutableRegistration.builder()
                .check(single)
                .checks(regChecks)
                .address("localhost")
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();
        client.agentClient().register(reg);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(3));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldRegisterChecksFromCleanState() {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        List<Registration.RegCheck> regChecks = ImmutableList.of(
                Registration.RegCheck.args(Collections.singletonList("/usr/bin/echo \"sup\""), 10, 1, "Custom description."),
                Registration.RegCheck.http("http://localhost:8080/health", 10, 1, "Custom description."));

        Registration reg = ImmutableRegistration.builder()
                .checks(regChecks)
                .address("localhost")
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();

        client.agentClient().register(reg, QueryOptions.BLANK);

        Synchroniser.pause(Duration.ofMillis(100));

        List<Registration.RegCheck> regCheck = ImmutableList.of(
                Registration.RegCheck.args(Collections.singletonList("/usr/bin/echo \"sup\""), 10, 1, "Custom description."));

        Registration secondRegistration = ImmutableRegistration.builder()
                .checks(regCheck)
                .address("localhost")
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();

        ImmutableQueryParameterOptions queryParameterOptions = ImmutableQueryParameterOptions.builder()
                .replaceExistingChecks(true)
                .build();

        client.agentClient().register(secondRegistration, QueryOptions.BLANK, queryParameterOptions);

        Synchroniser.pause(Duration.ofMillis(100));

        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
                assertThat(health.getChecks().size(), is(2));
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldDeregister() throws UnknownHostException, InterruptedException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 10000L, serviceName, serviceId, NO_TAGS, NO_META);
        client.agentClient().deregister(serviceId);
        Synchroniser.pause(Duration.ofSeconds(1));
        boolean found = false;

        for (ServiceHealth health : client.healthClient().getAllServiceInstances(serviceName).getResponse()) {
            if (health.getService().getId().equals(serviceId)) {
                found = true;
            }
        }

        assertFalse(found);
    }

    @Test
    public void shouldGetChecks() {
        String id = UUID.randomUUID().toString();
        client.agentClient().register(8080, 20L, UUID.randomUUID().toString(), id, NO_TAGS, NO_META);

        boolean found = false;

        for (Map.Entry<String, HealthCheck> check : client.agentClient().getChecks().entrySet()) {
            if (check.getValue().getCheckId().equals("service:" + id)) {
                found = true;
            }
        }

        assertTrue(found);
    }

    @Test
    public void shouldGetServices() {
        String id = UUID.randomUUID().toString();
        String name = UUID.randomUUID().toString();
        List<String> tags = Collections.singletonList(UUID.randomUUID().toString());
        Map<String, String> meta = Collections.singletonMap(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        client.agentClient().register(8080, 20L, name, id, tags, meta);
        Synchroniser.pause(Duration.ofMillis(100));

        Service expectedService = ImmutableService.builder()
                .id(id)
                .service(name)
                .address("")
                .port(8080)
                .tags(tags)
                .meta(meta)
                .enableTagOverride(false)
                .weights(ImmutableServiceWeights.builder().warning(1).passing(1).build())
                .build();
        Service registeredService = null;
        for (Map.Entry<String, Service> service : client.agentClient().getServices().entrySet()) {
            if (service.getValue().getId().equals(id)) {
                registeredService = service.getValue();
            }
        }

        assertNotNull(String.format("Service \"%s\" not found", name), registeredService);
        assertEquals(expectedService, registeredService);
    }

    @Test
    public void shouldGetServicesFiltered() {
        String id = UUID.randomUUID().toString();
        String name = UUID.randomUUID().toString();
        List<String> tags = Collections.singletonList(UUID.randomUUID().toString());
        String metaKey = "MetaKey";
        String metaValue = UUID.randomUUID().toString();
        Map<String, String> meta = Collections.singletonMap(metaKey, metaValue);
        client.agentClient().register(8080, 20L, name, id, tags, meta);
        Synchroniser.pause(Duration.ofMillis(100));

        Service expectedService = ImmutableService.builder()
                .id(id)
                .service(name)
                .address("")
                .port(8080)
                .tags(tags)
                .meta(meta)
                .enableTagOverride(false)
                .weights(ImmutableServiceWeights.builder().warning(1).passing(1).build())
                .build();
        Service registeredService = null;
        Map<String, Service> services = client.agentClient().getServices(
                ImmutableQueryOptions.builder()
                        .filter(String.format("Meta.%s == `%s`", metaKey, metaValue))
                        .build()
        );
        for (Map.Entry<String, Service> service : services.entrySet()) {
            if (service.getValue().getId().equals(id)) {
                registeredService = service.getValue();
            }
        }

        assertNotNull(String.format("Service \"%s\" not found", name), registeredService);
        assertEquals(expectedService, registeredService);
    }

    @Test
    public void shouldGetService() throws NotRegisteredException {
        String id = UUID.randomUUID().toString();
        String name = UUID.randomUUID().toString();
        List<String> tags = Collections.singletonList(UUID.randomUUID().toString());
        Map<String, String> meta = Collections.singletonMap(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        client.agentClient().register(8080, 20L, name, id, tags, meta);
        Synchroniser.pause(Duration.ofMillis(100));

        ConsulResponse<FullService> service = client.agentClient().getService(id, QueryOptions.BLANK);

        FullService expectedService = ImmutableFullService.builder()
                .id(id)
                .service(name)
                .address("")
                .port(8080)
                .tags(tags)
                .meta(meta)
                .enableTagOverride(false)
                .weights(ImmutableServiceWeights.builder().warning(1).passing(1).build())
                .contentHash(service.getResponse().getContentHash())
                .build();

        assertEquals(expectedService, service.getResponse());
    }

    @Test
    public void shouldGetServiceWithWait() throws NotRegisteredException {
        String id = UUID.randomUUID().toString();
        String name = UUID.randomUUID().toString();
        List<String> tags = Collections.singletonList(UUID.randomUUID().toString());
        Map<String, String> meta = Collections.singletonMap(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        client.agentClient().register(8080, 20L, name, id, tags, meta);
        Synchroniser.pause(Duration.ofMillis(100));

        ConsulResponse<FullService> service = client.agentClient().getService(id, QueryOptions.BLANK);
        ConsulResponse<FullService> other = client.agentClient().getService(id,
                QueryOptions.blockSeconds(20, service.getResponse().getContentHash()).build());

        assertEquals(service.getResponse(), other.getResponse());
    }

    @Test(expected = NotRegisteredException.class)
    public void shouldGetServiceThrowErrorWhenServiceIsUnknown() throws NotRegisteredException {
        client.agentClient().getService(UUID.randomUUID().toString(), QueryOptions.BLANK);
    }

    @Test
    public void shouldSetWarning() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String note = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId, Collections.emptyList(), Collections.emptyMap());
        client.agentClient().warn(serviceId, note);

        verifyState("warning", client, serviceId, serviceName, note);
    }

    @Test
    public void shouldSetFailing() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String note = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId, Collections.emptyList(), Collections.emptyMap());
        client.agentClient().fail(serviceId, note);

        verifyState("critical", client, serviceId, serviceName, note);
    }

    @Test
    public void shouldRegisterNodeScriptCheck() throws InterruptedException {
        String checkId = UUID.randomUUID().toString();

        client.agentClient().registerCheck(checkId, "test-validate", "/usr/bin/echo \"sup\"", 30);
        try {

            HealthCheck check = client.agentClient().getChecks().get(checkId);

            assertEquals(check.getCheckId(), checkId);
            assertEquals(check.getName(), "test-validate");
        }
        finally {
            client.agentClient().deregisterCheck(checkId);
        }
    }

    @Test
    public void shouldRegisterNodeHttpCheck() throws InterruptedException, MalformedURLException {
        String checkId = UUID.randomUUID().toString();

        client.agentClient().registerCheck(checkId, "test-validate", new URL("http://foo.local:1337/check"), 30);

        try {
            HealthCheck check = client.agentClient().getChecks().get(checkId);

            assertEquals(check.getCheckId(), checkId);
            assertEquals(check.getName(), "test-validate");
        }
        finally {
            client.agentClient().deregisterCheck(checkId);
        }
    }

    @Test
    public void shouldRegisterNodeTtlCheck() throws InterruptedException, MalformedURLException {
        String checkId = UUID.randomUUID().toString();

        client.agentClient().registerCheck(checkId, "test-validate", 30);
        try {
            HealthCheck check = client.agentClient().getChecks().get(checkId);

            assertEquals(check.getCheckId(), checkId);
            assertEquals(check.getName(), "test-validate");
        }
        finally {
            client.agentClient().deregisterCheck(checkId);
        }
    }

    @Test
    public void shouldEnableMaintenanceMode() throws InterruptedException, MalformedURLException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String reason = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);

        List<String> healthCheckNames = getHealthCheckNames(serviceName);
        assertFalse(healthCheckNames.contains("Service Maintenance Mode"));

        client.agentClient().toggleMaintenanceMode(serviceId, true, reason);

        List<String> updatedHealthCheckNames = getHealthCheckNames(serviceName);
        assertTrue(updatedHealthCheckNames.contains("Service Maintenance Mode"));
    }

    private List<String> getHealthCheckNames(String serviceName) {
        CatalogService catalogService = client.catalogClient().getService(serviceName).getResponse().get(0);
        String node = catalogService.getNode();
        List<String> healthCheckNames = client.healthClient()
                .getNodeChecks(node, QueryOptions.BLANK).getResponse()
                .stream()
                .map(HealthCheck::getName)
                .collect(toList());
        return healthCheckNames;
    }


    private void verifyState(String state, Consul client, String serviceId,
                             String serviceName, String output) throws UnknownHostException {

        Map<String, HealthCheck> checks = client.agentClient().getChecks();
        HealthCheck check = checks.get("service:" + serviceId);

        assertNotNull(check);
        assertEquals(serviceId, check.getServiceId().get());
        assertEquals(serviceName, check.getServiceName().get());
        assertEquals(state, check.getStatus());

        if (output != null) {
            assertEquals(output, check.getOutput().get());
        }
    }
}
