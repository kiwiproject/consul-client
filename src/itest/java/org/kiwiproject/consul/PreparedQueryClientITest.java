package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.query.Failover;
import org.kiwiproject.consul.model.query.ImmutableFailover;
import org.kiwiproject.consul.model.query.ImmutablePreparedQuery;
import org.kiwiproject.consul.model.query.ImmutableServiceQuery;
import org.kiwiproject.consul.model.query.PreparedQuery;
import org.kiwiproject.consul.model.query.StoredQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class PreparedQueryClientITest extends BaseIntegrationTest {

    private PreparedQueryClient preparedQueryClient;
    private List<String> queryIdsToDelete;

    @BeforeEach
    void setUp() {
        preparedQueryClient = client.preparedQueryClient();
        queryIdsToDelete = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        queryIdsToDelete.forEach(id -> preparedQueryClient.deletePreparedQuery(id));
    }

    @Test
    void shouldCreateAndFindPreparedQuery() {
        var serviceName = randomUUIDString();
        var query = randomUUIDString();
        client.agentClient().register(8080, 10000L, serviceName, serviceName + "1", List.of(), Map.of());

        var preparedQuery = ImmutablePreparedQuery.builder()
                .name(query)
                .token("")
                .service(ImmutableServiceQuery.builder()
                        .service(serviceName)
                        .onlyPassing(true)
                        .build())
                .build();

        var id = createPreparedQuery(preparedQuery);

        Optional<StoredQuery> maybeStoredQuery = preparedQueryClient.getPreparedQuery(id);
        assertThat(maybeStoredQuery).isPresent();

        var storedQuery = maybeStoredQuery.get();
        assertThat(storedQuery.getId()).isEqualTo(id);
        assertThat(storedQuery.getName()).isEqualTo(query);
        assertThat(storedQuery.getService().getService()).isEqualTo(serviceName);
        assertThat(storedQuery.getService().getFailover()).isPresent();
        assertThat(storedQuery.getService().getFailover().get().datacenters()).isEmpty();
    }

    @Test
    void shouldCreatePreparedQueryWithFailoverProperties() {
        var serviceName = randomUUIDString();
        var query = randomUUIDString();
        client.agentClient().register(8080, 10000L, serviceName, serviceName + "1", List.of(), Map.of());

        var preparedQuery = ImmutablePreparedQuery.builder()
                .name(query)
                .token("")
                .service(ImmutableServiceQuery.builder()
                        .service(serviceName)
                        .onlyPassing(true)
                        .failover(ImmutableFailover.builder()
                                .nearestN(3)
                                .datacenters(List.of("dc1", "dc2"))
                                .build())
                        .build())
                .build();

        var id = createPreparedQuery(preparedQuery);

        Optional<StoredQuery> maybeStoredQuery = preparedQueryClient.getPreparedQuery(id);
        assertThat(maybeStoredQuery).isPresent();

        var storedQuery = maybeStoredQuery.get();
        assertThat(storedQuery.getId()).isEqualTo(id);
        assertThat(storedQuery.getName()).isEqualTo(query);

        Optional<Failover> maybeFailover = storedQuery.getService().getFailover();
        assertThat(maybeFailover).isPresent();

        var failover = maybeFailover.get();
        assertThat(failover.getNearestN()).contains(3);
        assertThat(failover.datacenters()).contains(List.of("dc1", "dc2"));
    }

    @Test
    void shouldListPreparedQueries() {
        var serviceName1 = randomUUIDString();
        var query1 = randomUUIDString();
        client.agentClient().register(8080, 10000L, serviceName1, serviceName1 + "_id", List.of(), Map.of());

        var serviceName2 = randomUUIDString();
        var query2 = randomUUIDString();
        client.agentClient().register(8080, 10000L, serviceName2, serviceName2 + "_id", List.of(), Map.of());

        var preparedQuery1 = ImmutablePreparedQuery.builder()
                .name(query1)
                .token("")
                .service(ImmutableServiceQuery.builder()
                        .service(serviceName1)
                        .onlyPassing(true)
                        .build())
                .build();

        var id1 = createPreparedQuery(preparedQuery1);

        var preparedQuery2 = ImmutablePreparedQuery.builder()
                .name(query2)
                .token("")
                .service(ImmutableServiceQuery.builder()
                        .service(serviceName1)
                        .onlyPassing(true)
                        .build())
                .build();

        var id2 = createPreparedQuery(preparedQuery2);

        List<StoredQuery> storedQueries = preparedQueryClient.getPreparedQueries();

        assertThat(storedQueries).hasSize(2);

        List<String> queryIds = storedQueries.stream().map(StoredQuery::getId).toList();
        assertThat(queryIds).contains(id1, id2);

        List<String> queryNames = storedQueries.stream().map(StoredQuery::getName).toList();
        assertThat(queryNames).contains(query1, query2);
    }

    /**
     * Create a PreparedQuery which will be automatically deleted after test execution
     *
     * @return the ID of the stored PrepareQuery
     */
    private String createPreparedQuery(PreparedQuery preparedQuery) {
        var id = preparedQueryClient.createPreparedQuery(preparedQuery);
        queryIdsToDelete.add(id);
        return id;
    }
}
