package com.orbitz.consul;

import static com.orbitz.consul.Awaiting.awaitAtMost500ms;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.agent.FullService;
import com.orbitz.consul.model.agent.ImmutableFullService;
import com.orbitz.consul.model.agent.ImmutableRegCheck;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.catalog.ImmutableServiceWeights;
import com.orbitz.consul.model.health.HealthCheck;
import com.orbitz.consul.model.health.ImmutableService;
import com.orbitz.consul.model.health.Service;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.ImmutableQueryParameterOptions;
import com.orbitz.consul.option.QueryOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

class AgentClientITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = List.of();
    private static final Map<String, String> NO_META = Map.of();

    private AgentClient agentClient;

    @BeforeEach
    void setUp() {
        agentClient = client.agentClient();
    }

    @Test
    void shouldRetrieveAgentInformation() {
        var agent = agentClient.getAgent();

        assumeThat(agent.getDebugConfig()).isNotNull();

        assertThat(agent).isNotNull();
        assertThat(agent.getConfig()).isNotNull();
        assertThat(agent.getDebugConfig()).isNotNull();
        final List<?> clientAddrs = (List<?>) agent.getDebugConfig().get("ClientAddrs");
        assertThat(clientAddrs.get(0)).isNotNull();

        // maybe we should not make any assertion on the actual value of the client address
        // as like when we run consul in a docker container we would have "0.0.0.0"
        assertThat(clientAddrs.get(0)).isIn("127.0.0.1", "0.0.0.0");
    }

    @Test
    void shouldRegisterTtlCheck() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 10000L, serviceName, serviceId, NO_TAGS, NO_META);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(2);
    }

    @Test
    void shouldRegisterHttpCheck() throws MalformedURLException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        var healthCheclkUrl = URI.create("http://localhost:1337/health").toURL();
        agentClient.register(8080, healthCheclkUrl, 1000L, serviceName, serviceId, NO_TAGS, NO_META);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(2);
    }

    @Test
    void shouldRegisterGrpcCheck() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        var registration = ImmutableRegistration.builder()
                .name(serviceName)
                .id(serviceId)
                .port(12345)
                .addChecks(ImmutableRegCheck.builder()
                        .grpc("localhost:12345")
                        .interval("10s")
                        .build())
                .build();
        agentClient.register(registration);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(2);
    }

    @Test
    void shouldRegisterCheckWithId() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();
        var checkId = randomUUIDString();

        var registration = ImmutableRegistration.builder()
                .name(serviceName)
                .id(serviceId)
                .port(8085)
                .addChecks(ImmutableRegCheck.builder()
                        .id(checkId)
                        .ttl("10s")
                        .build())
                .build();

        agentClient.register(registration);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(2);
        assertThat(serviceHealth.getChecks().stream().anyMatch(check -> check.getCheckId().equals(checkId))).isTrue();
    }

    @Test
    void shouldRegisterCheckWithName() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();
        var checkName = randomUUIDString();

        var registration = ImmutableRegistration.builder()
                .name(serviceName)
                .id(serviceId)
                .port(9142)
                .addChecks(ImmutableRegCheck.builder()
                        .name(checkName)
                        .ttl("10s")
                        .build())
                .build();

        agentClient.register(registration);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(2);
        assertThat(serviceHealth.getChecks().stream().anyMatch(check -> check.getName().equals(checkName))).isTrue();
    }

    @Test
    void shouldRegisterMultipleChecks() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        var regChecks = List.of(
                Registration.RegCheck.args(List.of("/usr/bin/echo \"sup\""), 10, 1, "Custom description."),
                Registration.RegCheck.http("http://localhost:8080/health", 10, 1, "Custom description."));

        agentClient.register(8080, regChecks, serviceName, serviceId, NO_TAGS, NO_META);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(3);
    }

    // This is apparently valid
    // to register a single "Check"
    // and multiple "Checks" in one call
    @Test
    void shouldRegisterMultipleChecks2() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        var singleCheck = Registration.RegCheck.args(List.of("/usr/bin/echo \"sup\""), 10);

        var regChecks = List.of(Registration.RegCheck.http("http://localhost:8080/health", 10));

        var reg = ImmutableRegistration.builder()
                .check(singleCheck)
                .checks(regChecks)
                .address("localhost")
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();
        agentClient.register(reg);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(3);
    }

    @Test
    void shouldRegisterChecksFromCleanState() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        var regChecks = List.of(
                Registration.RegCheck.args(List.of("/usr/bin/echo \"sup\""), 10, 1, "Custom description."),
                Registration.RegCheck.http("http://localhost:8080/health", 10, 1, "Custom description."));

        var reg = ImmutableRegistration.builder()
                .checks(regChecks)
                .address("localhost")
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();

        agentClient.register(reg, QueryOptions.BLANK);

        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId));

        var regCheck = List.of(
                Registration.RegCheck.args(List.of("/usr/bin/echo \"sup\""), 10, 1, "Custom description."));

        var secondRegistration = ImmutableRegistration.builder()
                .checks(regCheck)
                .address("localhost")
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();

        var queryParameterOptions = ImmutableQueryParameterOptions.builder()
                .replaceExistingChecks(true)
                .build();

        agentClient.register(secondRegistration, QueryOptions.BLANK, queryParameterOptions);

        var serviceHealthRef = new AtomicReference<ServiceHealth>();
        awaitAtMost500ms().until(() -> serviceHealthExistsWithNameAndId(serviceName, serviceId, serviceHealthRef));

        var serviceHealth = getValueOrFail(serviceHealthRef);
        assertThat(serviceHealth.getChecks()).hasSize(2);
    }

    @Test
    void shouldDeregister() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 10000L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.deregister(serviceId);

        awaitAtMost500ms().until(() -> !serviceHealthExistsWithNameAndId(serviceName, serviceId));
    }

    @Test
    void shouldGetChecks() {
        var serviceId = randomUUIDString();
        var serviceName = randomUUIDString();
        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);

        awaitAtMost500ms().until(() -> checkExistsWithId("service:" + serviceId));
    }

    @Test
    void shouldGetServices() {
        var serviceId = randomUUIDString();
        var serviceName = randomUUIDString();
        var tags = List.of(randomUUIDString());
        var meta = Map.of(randomUUIDString(), randomUUIDString());

        agentClient.register(8080, 20L, serviceName, serviceId, tags, meta);

        awaitAtMost500ms().until(() -> serviceExistsWithId(serviceId));

        var expectedService = ImmutableService.builder()
                .id(serviceId)
                .service(serviceName)
                .address("")
                .port(8080)
                .tags(tags)
                .meta(meta)
                .enableTagOverride(false)
                .weights(ImmutableServiceWeights.builder().warning(1).passing(1).build())
                .build();

        var registeredService = findService(service -> service.getId().equals(serviceId)).orElseThrow();
        assertThat(registeredService).isEqualTo(expectedService);
    }

    @Test
    void shouldGetServicesFiltered() {
        var serviceId = randomUUIDString();
        var serviceName = randomUUIDString();
        var tags = List.of(randomUUIDString());
        var metaKey = "MetaKey";
        var metaValue = randomUUIDString();
        var meta = Map.of(metaKey, metaValue);

        agentClient.register(8080, 20L, serviceName, serviceId, tags, meta);

        awaitAtMost500ms().until(() -> serviceExistsWithId(serviceId));

        var expectedService = ImmutableService.builder()
                .id(serviceId)
                .service(serviceName)
                .address("")
                .port(8080)
                .tags(tags)
                .meta(meta)
                .enableTagOverride(false)
                .weights(ImmutableServiceWeights.builder().warning(1).passing(1).build())
                .build();

        var queryOptions = ImmutableQueryOptions.builder()
                .filter(String.format("Meta.%s == `%s`", metaKey, metaValue))
                .build();
        var services = agentClient.getServices(queryOptions).values();

        var registeredService = findService(services, service -> service.getId().equals(serviceId)).orElseThrow();
        assertThat(registeredService).isEqualTo(expectedService);
    }

    @Test
    void shouldGetService() throws NotRegisteredException {
        var serviceId = randomUUIDString();
        var serviceName = randomUUIDString();
        var tags = List.of(randomUUIDString());
        var meta = Map.of(randomUUIDString(), randomUUIDString());

        agentClient.register(8080, 20L, serviceName, serviceId, tags, meta);

        awaitAtMost500ms().until(() -> serviceExistsWithId(serviceId));

        ConsulResponse<FullService> service = agentClient.getService(serviceId, QueryOptions.BLANK);

        FullService expectedService = ImmutableFullService.builder()
                .id(serviceId)
                .service(serviceName)
                .address("")
                .port(8080)
                .tags(tags)
                .meta(meta)
                .enableTagOverride(false)
                .weights(ImmutableServiceWeights.builder().warning(1).passing(1).build())
                .contentHash(service.getResponse().getContentHash())
                .build();

        assertThat(service.getResponse()).isEqualTo(expectedService);
    }

    @Test
    void shouldGetServiceWithWait() throws NotRegisteredException {
        var serviceId = randomUUIDString();
        var serviceName = randomUUIDString();
        var tags = List.of(randomUUIDString());
        var meta = Map.of(randomUUIDString(), randomUUIDString());

        var ttl = 2;  // seconds
        agentClient.register(8080, ttl, serviceName, serviceId, tags, meta);

        awaitAtMost500ms().until(() -> serviceExistsWithId(serviceId));

        ConsulResponse<FullService> service = agentClient.getService(serviceId, QueryOptions.BLANK);

        var blockingQueryOptions = QueryOptions.blockSeconds(ttl, service.getResponse().getContentHash()).build();

        var start = System.nanoTime();
        ConsulResponse<FullService> other = agentClient.getService(serviceId, blockingQueryOptions);
        var elapsed = System.nanoTime() - start;

        assertThat(other.getResponse()).isEqualTo(service.getResponse());
        assertThat(TimeUnit.NANOSECONDS.toMillis(elapsed))
                .describedAs("Elapsed time should be equal or more than blocking time")
                .isGreaterThanOrEqualTo(TimeUnit.SECONDS.toMillis(ttl));
    }

    @Test
    void shouldGetServiceThrowErrorWhenServiceIsUnknown() {
        assertThatExceptionOfType(NotRegisteredException.class).isThrownBy(() ->
                agentClient.getService(randomUUIDString(), QueryOptions.BLANK));
    }

    @Test
    void shouldSetWarning() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();
        var note = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, List.of(), Map.of());

        awaitAtMost500ms().until(() -> serviceExistsWithId(serviceId));

        agentClient.warn(serviceId, note);

        verifyState("warning", client, serviceId, serviceName, note);
    }

    @Test
    void shouldSetFailing() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();
        var note = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, List.of(), Map.of());

        awaitAtMost500ms().until(() -> serviceExistsWithId(serviceId));

        agentClient.fail(serviceId, note);

        verifyState("critical", client, serviceId, serviceName, note);
    }

    @Test
    void shouldRegisterNodeScriptCheck() {
        var checkId = randomUUIDString();

        agentClient.registerCheck(checkId, "test-validate", "/usr/bin/echo \"sup\"", 30);

        awaitAtMost500ms().until(() -> checkExistsWithId(checkId));

        try {
            HealthCheck check = agentClient.getChecks().get(checkId);

            assertThat(checkId).isEqualTo(check.getCheckId());
            assertThat(check.getName()).isEqualTo("test-validate");
        } finally {
            agentClient.deregisterCheck(checkId);
        }
    }

    @Test
    void shouldRegisterNodeHttpCheck() throws MalformedURLException {
        var checkId = randomUUIDString();

        var healthCheckUrl = URI.create("http://foo.local:1337/check").toURL();
        agentClient.registerCheck(checkId, "test-validate", healthCheckUrl, 30);

        awaitAtMost500ms().until(() -> checkExistsWithId(checkId));

        try {
            HealthCheck check = agentClient.getChecks().get(checkId);

            assertThat(checkId).isEqualTo(check.getCheckId());
            assertThat(check.getName()).isEqualTo("test-validate");
        } finally {
            agentClient.deregisterCheck(checkId);
        }
    }

    @Test
    void shouldRegisterNodeTtlCheck() {
        var checkId = randomUUIDString();

        agentClient.registerCheck(checkId, "test-validate", 30);

        awaitAtMost500ms().until(() -> checkExistsWithId(checkId));

        try {
            HealthCheck check = agentClient.getChecks().get(checkId);

            assertThat(checkId).isEqualTo(check.getCheckId());
            assertThat(check.getName()).isEqualTo("test-validate");
        } finally {
            agentClient.deregisterCheck(checkId);
        }
    }

    @Test
    void shouldEnableMaintenanceMode() {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();
        var reason = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);

        awaitAtMost500ms().until(() -> serviceExistsWithId(serviceId));

        List<String> healthCheckNames = getHealthCheckNames(serviceName);
        assertThat(healthCheckNames)
                .isNotEmpty()
                .doesNotContain("Service Maintenance Mode");

        agentClient.toggleMaintenanceMode(serviceId, true, reason);

        List<String> updatedHealthCheckNames = getHealthCheckNames(serviceName);
        assertThat(updatedHealthCheckNames).contains("Service Maintenance Mode");
    }

    private boolean serviceExistsWithId(String serviceId) {
        return serviceExistsWithId(serviceId, new AtomicReference<>());
    }

    private boolean serviceExistsWithId(String serviceId, AtomicReference<Service> serviceRef) {
        var services = agentClient.getServices().values();
        var serviceOptional = findService(services, service -> service.getId().equals(serviceId));

        serviceRef.set(serviceOptional.orElse(null));

        return serviceOptional.isPresent();
    }

    private Optional<Service> findService(Predicate<Service> predicate) {
        var services = agentClient.getServices().values();
        return findService(services, predicate);
    }

    private Optional<Service> findService(Collection<Service> services, Predicate<Service> predicate) {
        return findService(services.stream(), predicate);
    }

    private Optional<Service> findService(Stream<Service> services, Predicate<Service> predicate) {
        return services.filter(predicate).findFirst();
    }

    private boolean checkExistsWithId(String checkId) {
        return agentClient.getChecks()
                .values()
                .stream()
                .anyMatch(healthCheck -> healthCheck.getCheckId().equals(checkId));
    }

    private boolean serviceHealthExistsWithNameAndId(String serviceName, String serviceId) {
        return serviceHealthExistsWithNameAndId(serviceName, serviceId, new AtomicReference<>());
    }

    private boolean serviceHealthExistsWithNameAndId(String serviceName,
                                                     String serviceId,
                                                     AtomicReference<ServiceHealth> serviceHealthRef) {

        var serviceHealthList = client.healthClient().getAllServiceInstances(serviceName).getResponse();
        var serviceHealthOptional = serviceHealthList.stream()
                .filter(serviceHealth -> serviceHealth.getService().getId().equals(serviceId))
                .findFirst();

        serviceHealthRef.set(serviceHealthOptional.orElse(null));

        return serviceHealthOptional.isPresent();
    }

    private static <T> T getValueOrFail(AtomicReference<T> ref) {
        assertThat(ref.get()).isNotNull();
        return ref.get();
    }

    private List<String> getHealthCheckNames(String serviceName) {
        var catalogService = client.catalogClient().getService(serviceName).getResponse().get(0);
        String node = catalogService.getNode();
        return client.healthClient()
                .getNodeChecks(node, QueryOptions.BLANK).getResponse()
                .stream()
                .map(HealthCheck::getName)
                .collect(toList());
    }

    private void verifyState(String state, Consul client, String serviceId, String serviceName, String output) {
        Map<String, HealthCheck> checks = client.agentClient().getChecks();
        HealthCheck check = checks.get("service:" + serviceId);

        assertThat(check).isNotNull();
        assertThat(check.getServiceId().orElseThrow()).isEqualTo(serviceId);
        assertThat(check.getServiceName().orElseThrow()).isEqualTo(serviceName);
        assertThat(check.getStatus()).isEqualTo(state);

        if (nonNull(output)) {
            assertThat(check.getOutput().orElseThrow()).isEqualTo(output);
        }
    }
}
