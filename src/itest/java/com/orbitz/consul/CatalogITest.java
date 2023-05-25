package com.orbitz.consul;

import static com.orbitz.consul.Awaiting.awaitWith25MsPoll;
import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.Objects.nonNull;
import static org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.net.UnknownHostException;
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
    void shouldGetNodes() throws UnknownHostException {
        assertFalse(catalogClient.getNodes().getResponse().isEmpty());
    }

    @Test
    void shouldGetNodesByDatacenter() throws UnknownHostException {
        var queryOptions = ImmutableQueryOptions.builder().datacenter("dc1").build();
        assertFalse(catalogClient.getNodes(queryOptions).getResponse().isEmpty());
    }

    /**
     * @implNote This test necessarily blocks, and QueryOptions doesn't support granularity less than
     * seconds, so the minimum time that can be used here is one second.
     */
    @Test
    void shouldGetNodesByDatacenterBlock() throws UnknownHostException {
        var start = System.nanoTime();
        var index = new BigInteger(Integer.toString(Integer.MAX_VALUE));
        var queryOptions = QueryOptions
                .blockSeconds(1, index)
                .datacenter("dc1")
                .build();
        ConsulResponse<List<Node>> response = catalogClient.getNodes(queryOptions);
        var time = System.nanoTime() - start;

        assertTrue(time >= TimeUnit.SECONDS.toNanos(1));
        assertFalse(response.getResponse().isEmpty());
    }

    @Test
    void shouldGetDatacenters() throws UnknownHostException {
        List<String> datacenters = catalogClient.getDatacenters();

        assertEquals(1, datacenters.size());
        assertEquals("dc1", datacenters.iterator().next());
    }

    @Test
    void shouldGetServices() throws Exception {
        ConsulResponse<Map<String, List<String>>> services = catalogClient.getServices();

        assertTrue(services.getResponse().containsKey("consul"));
    }

    @Test
    void shouldGetService() throws Exception {
        ConsulResponse<List<CatalogService>> services = catalogClient.getService("consul");

        assertEquals("consul", services.getResponse().iterator().next().getServiceName());
    }

    @Test
    void shouldGetNode() throws Exception {
        ConsulResponse<CatalogNode> node = catalogClient.getNode(catalogClient.getNodes()
                .getResponse().iterator().next().getNode());

        assertNotNull(node);
    }

    @Test
    void shouldGetTaggedAddressesForNodesLists() throws UnknownHostException {
        final List<Node> nodesResp = catalogClient.getNodes().getResponse();
        assertFalse(nodesResp.isEmpty());
        for (Node node : nodesResp) {
            assertNotNull(node.getTaggedAddresses());
            if (node.getTaggedAddresses().isPresent()) {
                assertNotNull(node.getTaggedAddresses().get().getWan());
                assertFalse(node.getTaggedAddresses().get().getWan().isEmpty());
            }
        }
    }

    @Test
    void shouldGetTaggedAddressesForNode() throws UnknownHostException {
        final List<Node> nodesResp = catalogClient.getNodes().getResponse();
        assertFalse(nodesResp.isEmpty());
        for (Node tmp : nodesResp) {
            final Node node = catalogClient.getNode(tmp.getNode()).getResponse().getNode();
            assertNotNull(node.getTaggedAddresses());
            if (node.getTaggedAddresses().isPresent()) {
                assertNotNull(node.getTaggedAddresses().get().getWan());
                assertFalse(node.getTaggedAddresses().get().getWan().isEmpty());
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
    void shouldDeregisterWithDefaultDC() throws InterruptedException {
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

        assertTrue(result.containsKey(serviceName));
    }

    @Test
    void shouldGetServiceInCallback() throws ExecutionException, InterruptedException, TimeoutException {
        String serviceName = randomUUIDString();
        String serviceId = createAutoDeregisterServiceId();
        client.agentClient().register(20001, 20, serviceName, serviceId, List.of(), Map.of());

        CompletableFuture<List<CatalogService>> cf = new CompletableFuture<>();
        catalogClient.getService(serviceName, QueryOptions.BLANK, callbackFuture(cf));

        List<CatalogService> result = cf.get(1, TimeUnit.SECONDS);

        assertEquals(1, result.size());
        CatalogService service = result.get(0);

        assertEquals(serviceId, service.getServiceId());
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

        assertEquals(nodeName, node.getNode().getNode());

        Service service = node.getServices().get(serviceId);
        assertNotNull(service);
        assertEquals(serviceName, service.getService());
    }

    private static <T> ConsulResponseCallback<T> callbackFuture(CompletableFuture<T> cf) {
        return new ConsulResponseCallback<T>() {
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

        var serviceName = registration.service().get().getService();

        var foundServiceRef = new AtomicReference<CatalogService>();
        awaitWith25MsPoll()
                .atMost(FIVE_HUNDRED_MILLISECONDS)
                .until(() -> serviceWithNameExists(serviceName, foundServiceRef));

        assertNotNull(foundServiceRef.get());
        assertEquals(expectedService, foundServiceRef.get());
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
