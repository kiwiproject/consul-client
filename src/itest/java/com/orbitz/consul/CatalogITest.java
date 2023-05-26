package com.orbitz.consul;

import static com.orbitz.consul.Awaiting.awaitWith25MsPoll;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;

import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.catalog.CatalogDeregistration;
import com.orbitz.consul.model.catalog.CatalogNode;
import com.orbitz.consul.model.catalog.CatalogRegistration;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.catalog.ImmutableCatalogDeregistration;
import com.orbitz.consul.model.catalog.ImmutableCatalogRegistration;
import com.orbitz.consul.model.catalog.ImmutableCatalogService;
import com.orbitz.consul.model.catalog.ImmutableServiceWeights;
import com.orbitz.consul.model.health.ImmutableService;
import com.orbitz.consul.model.health.Node;
import com.orbitz.consul.model.health.Service;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.QueryOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

class CatalogITest extends BaseIntegrationTest {

    private CatalogClient catalogClient;

    @BeforeEach
    void setUp() {
        catalogClient = client.catalogClient();
    }

    @Test
    void shouldGetNodes() {
        assertThat(catalogClient.getNodes().getResponse()).isNotEmpty();
    }

    @Test
    void shouldGetNodesByDatacenter() {
        var queryOptions = ImmutableQueryOptions.builder().datacenter("dc1").build();
        assertThat(catalogClient.getNodes(queryOptions).getResponse()).isNotEmpty();
    }

    /**
     * @implNote This test necessarily blocks, and QueryOptions doesn't support granularity less than
     * seconds, so the minimum time that can be used here is one second.
     */
    @Test
    void shouldGetNodesByDatacenterBlock() {
        var start = System.nanoTime();
        var index = new BigInteger(Integer.toString(Integer.MAX_VALUE));
        var queryOptions = QueryOptions
                .blockSeconds(1, index)
                .datacenter("dc1")
                .build();
        ConsulResponse<List<Node>> response = catalogClient.getNodes(queryOptions);
        var time = System.nanoTime() - start;

        assertThat(time).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(1));
        assertThat(response.getResponse()).isNotEmpty();
    }

    @Test
    void shouldGetDatacenters() {
        List<String> datacenters = catalogClient.getDatacenters();

        assertThat(datacenters).hasSize(1);
        assertThat(datacenters.iterator().next()).isEqualTo("dc1");
    }

    @Test
    void shouldGetServices() {
        ConsulResponse<Map<String, List<String>>> services = catalogClient.getServices();

        assertThat(services.getResponse()).containsKey("consul");
    }

    @Test
    void shouldGetService() {
        ConsulResponse<List<CatalogService>> services = catalogClient.getService("consul");

        assertThat(services.getResponse().iterator().next().getServiceName()).isEqualTo("consul");
    }

    @Test
    void shouldGetNode() {
        ConsulResponse<CatalogNode> node = catalogClient.getNode(catalogClient.getNodes()
                .getResponse().iterator().next().getNode());

        assertThat(node).isNotNull();
    }

    @Test
    void shouldGetTaggedAddressesForNodesLists() {
        final List<Node> nodesResp = catalogClient.getNodes().getResponse();
        assertThat(nodesResp).isNotEmpty();
        for (Node node : nodesResp) {
            assertThat(node.getTaggedAddresses()).isNotNull();
            if (node.getTaggedAddresses().isPresent()) {
                assertThat(node.getTaggedAddresses().get().getWan()).isNotNull();
                assertThat(node.getTaggedAddresses().get().getWan()).isNotEmpty();
            }
        }
    }

    @Test
    void shouldGetTaggedAddressesForNode() {
        final List<Node> nodesResp = catalogClient.getNodes().getResponse();
        assertThat(nodesResp).isNotEmpty();
        for (Node tmp : nodesResp) {
            final Node node = catalogClient.getNode(tmp.getNode()).getResponse().getNode();
            assertThat(node.getTaggedAddresses()).isNotNull();
            if (node.getTaggedAddresses().isPresent()) {
                assertThat(node.getTaggedAddresses().get().getWan()).isNotNull();
                assertThat(node.getTaggedAddresses().get().getWan()).isNotEmpty();
            }
        }
    }

    @Test
    void shouldRegisterService() {
        String service = randomUUIDString();
        String serviceId = randomUUIDString();
        String catalogId = randomUUIDString();

        createAndCheckService(
                ImmutableCatalogService.builder()
                        .address("localhost")
                        .datacenter("dc1")
                        .node("node")
                        .serviceAddress("localhost")
                        .addServiceTags("sometag")
                        .serviceId(serviceId)
                        .serviceName(service)
                        .servicePort(8080)
                        .putServiceMeta("metakey", "metavalue")
                        .putNodeMeta("a", "b")
                        .serviceEnableTagOverride(true)
                        .serviceWeights(ImmutableServiceWeights.builder().passing(42).warning(21).build())
                        .build(),
                ImmutableCatalogRegistration.builder()
                        .id(catalogId)
                        .putNodeMeta("a", "b")
                        .address("localhost")
                        .datacenter("dc1")
                        .node("node")
                        .service(ImmutableService.builder()
                                .address("localhost")
                                .addTags("sometag")
                                .id(serviceId)
                                .service(service)
                                .port(8080)
                                .putMeta("metakey", "metavalue")
                                .enableTagOverride(true) //setting this request flag sets the ServiceEnableTagOverride in the response
                                .weights(ImmutableServiceWeights.builder().passing(42).warning(21).build())
                                .build())
                        .build()
        );
    }

    @Test
    void shouldRegisterServiceNoWeights() {
        String service = randomUUIDString();
        String serviceId = randomUUIDString();
        String catalogId = randomUUIDString();

        createAndCheckService(
                ImmutableCatalogService.builder()
                        .address("localhost")
                        .datacenter("dc1")
                        .node("node")
                        .serviceAddress("localhost")
                        .addServiceTags("sometag")
                        .serviceId(serviceId)
                        .serviceName(service)
                        .servicePort(8080)
                        .putServiceMeta("metakey", "metavalue")
                        .putNodeMeta("a", "b")
                        .serviceEnableTagOverride(true)
                        .serviceWeights(ImmutableServiceWeights.builder().passing(1).warning(1).build())
                        .build(),
                ImmutableCatalogRegistration.builder()
                        .id(catalogId)
                        .putNodeMeta("a", "b")
                        .address("localhost")
                        .datacenter("dc1")
                        .node("node")
                        .service(ImmutableService.builder()
                                .address("localhost")
                                .addTags("sometag")
                                .id(serviceId)
                                .service(service)
                                .port(8080)
                                .putMeta("metakey", "metavalue")
                                .enableTagOverride(true) //setting this request flag sets the ServiceEnableTagOverride in the response
                                .build())
                        .build()
        );
    }


    @Test
    void shouldDeregisterWithDefaultDC() {
        String service = randomUUIDString();
        String serviceId = randomUUIDString();
        String catalogId = randomUUIDString();

        CatalogRegistration registration = ImmutableCatalogRegistration.builder()
                .id(catalogId)
                .putNodeMeta("a", "b")
                .address("localhost")
                .datacenter("dc1")
                .node("node")
                .service(ImmutableService.builder()
                        .address("localhost")
                        .addTags("sometag")
                        .id(serviceId)
                        .service(service)
                        .port(8080)
                        .putMeta("metakey", "metavalue")
                        .build())
                .build();

        catalogClient.register(registration);

        awaitWith25MsPoll()
                .atMost(FIVE_HUNDRED_MILLISECONDS)
                .until(() -> serviceExists(service, serviceId));

        CatalogDeregistration deregistration = ImmutableCatalogDeregistration.builder()
                .node("node")
                .serviceId(serviceId)
                .build();

        catalogClient.deregister(deregistration);

        awaitWith25MsPoll()
                .atMost(FIVE_HUNDRED_MILLISECONDS)
                .until(() -> !serviceExists(service, serviceId));
    }

    private boolean serviceExists(String serviceName, String serviceId) {
        var serviceHealthList = client.healthClient().getAllServiceInstances(serviceName).getResponse();

        return serviceHealthList.stream()
                .anyMatch(health -> health.getService().getId().equals(serviceId));
    }

    @Test
    void shouldGetServicesInCallback() throws ExecutionException, InterruptedException, TimeoutException {
        String serviceName = randomUUIDString();
        String serviceId = createAutoDeregisterServiceId();
        client.agentClient().register(20001, 20, serviceName, serviceId, List.of(), Map.of());

        CompletableFuture<Map<String, List<String>>> cf = new CompletableFuture<>();
        catalogClient.getServices(QueryOptions.BLANK, callbackFuture(cf));

        Map<String, List<String>> result = cf.get(1, TimeUnit.SECONDS);

        assertThat(result).containsKey(serviceName);
    }

    @Test
    void shouldGetServiceInCallback() throws ExecutionException, InterruptedException, TimeoutException {
        String serviceName = randomUUIDString();
        String serviceId = createAutoDeregisterServiceId();
        client.agentClient().register(20001, 20, serviceName, serviceId, List.of(), Map.of());

        CompletableFuture<List<CatalogService>> cf = new CompletableFuture<>();
        catalogClient.getService(serviceName, QueryOptions.BLANK, callbackFuture(cf));

        List<CatalogService> result = cf.get(1, TimeUnit.SECONDS);

        assertThat(result).hasSize(1);
        CatalogService service = result.get(0);

        assertThat(service.getServiceId()).isEqualTo(serviceId);
    }

    @Test
    void shouldGetNodeInCallback() throws ExecutionException, InterruptedException, TimeoutException {
        String nodeName = "node";
        String serviceName = randomUUIDString();
        String serviceId = randomUUIDString();
        String catalogId = randomUUIDString();

        CatalogRegistration registration = ImmutableCatalogRegistration.builder()
                .id(catalogId)
                .putNodeMeta("a", "b")
                .address("localhost")
                .node(nodeName)
                .service(ImmutableService.builder()
                        .address("localhost")
                        .id(serviceId)
                        .service(serviceName)
                        .port(20001)
                        .build())
                .build();

        catalogClient.register(registration);

        CompletableFuture<CatalogNode> cf = new CompletableFuture<>();
        catalogClient.getNode(nodeName, QueryOptions.BLANK, callbackFuture(cf));

        CatalogNode node = cf.get(1, TimeUnit.SECONDS);

        assertThat(node.getNode().getNode()).isEqualTo(nodeName);

        Service service = node.getServices().get(serviceId);
        assertThat(service).isNotNull();
        assertThat(service.getService()).isEqualTo(serviceName);
    }

    private static <T> ConsulResponseCallback<T> callbackFuture(CompletableFuture<T> cf) {
        return new ConsulResponseCallback<>() {
            @Override
            public void onComplete(ConsulResponse<T> consulResponse) {
                cf.complete(consulResponse.getResponse());
            }

            @Override
            public void onFailure(Throwable throwable) {
                cf.completeExceptionally(throwable);
            }
        };
    }

    private void createAndCheckService(CatalogService expectedService, CatalogRegistration registration) {
        catalogClient.register(registration);

        assertThat(registration.service()).isPresent();
        var serviceName = registration.service().orElseThrow().getService();

        var foundServiceRef = new AtomicReference<CatalogService>();
        awaitWith25MsPoll()
                .atMost(FIVE_HUNDRED_MILLISECONDS)
                .until(() -> serviceWithNameExists(serviceName, foundServiceRef));

        assertThat(foundServiceRef.get()).isNotNull();
        assertThat(foundServiceRef.get()).isEqualTo(expectedService);
    }

    private boolean serviceWithNameExists(String serviceName, AtomicReference<CatalogService> foundServiceRef) {
        var catalogServices = catalogClient.getService(serviceName).getResponse();

        var foundService = catalogServices.stream()
                .filter(service -> service.getServiceName().equals(serviceName))
                .findFirst()
                .orElse(null);

        foundServiceRef.set(foundService);

        return nonNull(foundService);
    }
}
