package com.orbitz.consul;

import static com.orbitz.consul.Consul.builder;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.State;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.HealthCheck;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.QueryOptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

class HealthITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = List.of();
    private static final Map<String, String> NO_META = Map.of();

    private AgentClient agentClient;

    @BeforeEach
    void setUp() {
        agentClient = client.agentClient();
    }

    @Test
    void shouldFetchPassingNode() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        Consul client2 = builder().withHostAndPort(HostAndPort.fromParts("localhost", consulContainer.getFirstMappedPort())).build();
        var serviceId2 = randomUUIDString();

        client2.agentClient().register(8080, 20L, serviceName, serviceId2, NO_TAGS, NO_META);
        client2.agentClient().fail(serviceId2);

        ConsulResponse<List<ServiceHealth>> response = client2.healthClient().getHealthyServiceInstances(serviceName);
        assertHealth(serviceId, response);

        agentClient.deregister(serviceId);
        agentClient.deregister(serviceId2);
    }

    @Test
    void shouldFetchNode() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        ConsulResponse<List<ServiceHealth>> response = client.healthClient().getAllServiceInstances(serviceName);
        assertHealth(serviceId, response);

        agentClient.deregister(serviceId);
    }

    @Test
    void shouldFetchNodeDatacenter() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        ConsulResponse<List<ServiceHealth>> response = client.healthClient().getAllServiceInstances(serviceName,
                ImmutableQueryOptions.builder().datacenter("dc1").build());
        assertHealth(serviceId, response);
        agentClient.deregister(serviceId);
    }

    @Test
    void shouldFetchNodeBlock() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.pass(serviceId);

        ConsulResponse<List<ServiceHealth>> response = client.healthClient().getAllServiceInstances(serviceName,
                QueryOptions.blockSeconds(2, new BigInteger("0")).datacenter("dc1").build());
        assertHealth(serviceId, response);
        agentClient.deregister(serviceId);
    }

    @Test
    void shouldFetchChecksForServiceBlock() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        Registration.RegCheck check = Registration.RegCheck.ttl(5);
        Registration registration = ImmutableRegistration
                .builder()
                .check(check)
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();

        agentClient.register(registration);
        agentClient.pass(serviceId);

        var found = false;
        ConsulResponse<List<HealthCheck>> response = client.healthClient().getServiceChecks(serviceName,
                QueryOptions.blockSeconds(20, new BigInteger("0")).datacenter("dc1").build());

        List<HealthCheck> checks = response.getResponse();
        assertThat(checks).hasSize(1);
        for(HealthCheck ch : checks) {
            if(ch.getServiceId().isPresent() && ch.getServiceId().get().equals(serviceId)) {
                found = true;
            }
        }
        assertThat(found).isTrue();
        agentClient.deregister(serviceId);
    }

    @Test
    void shouldFetchByState() throws NotRegisteredException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        agentClient.register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        agentClient.warn(serviceId);

        var found = false;
        ConsulResponse<List<HealthCheck>> response = client.healthClient().getChecksByState(State.WARN);

        for (HealthCheck healthCheck : response.getResponse()) {
            if (healthCheck.getServiceId().isPresent() && healthCheck.getServiceId().get().equals(serviceId)) {
                found = true;
            }
        }

        assertThat(found).isTrue();
        agentClient.deregister(serviceId);
    }

    private void assertHealth(String serviceId, ConsulResponse<List<ServiceHealth>> response) {
        var found = false;
        List<ServiceHealth> nodes = response.getResponse();

        assertThat(nodes).hasSize(1);

        for (ServiceHealth health : nodes) {
            if(health.getService().getId().equals(serviceId)) {
                found = true;
            }
        }

        assertThat(found).isTrue();
    }
}
