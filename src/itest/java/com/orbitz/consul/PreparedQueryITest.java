package com.orbitz.consul;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orbitz.consul.model.query.Failover;
import com.orbitz.consul.model.query.ImmutableFailover;
import com.orbitz.consul.model.query.ImmutablePreparedQuery;
import com.orbitz.consul.model.query.ImmutableServiceQuery;
import com.orbitz.consul.model.query.PreparedQuery;
import com.orbitz.consul.model.query.StoredQuery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class PreparedQueryITest extends BaseIntegrationTest {

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
        assertTrue(maybeStoredQuery.isPresent());

        var storedQuery = maybeStoredQuery.get();
        assertThat(storedQuery.getId(), is(id));
        assertThat(storedQuery.getName(), is(query));
        assertThat(storedQuery.getService().getService(), is(serviceName));
        assertTrue(storedQuery.getService().getFailover().isPresent());
        assertTrue(storedQuery.getService().getFailover().get().datacenters().isEmpty());
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
        assertTrue(maybeStoredQuery.isPresent());

        var storedQuery = maybeStoredQuery.get();
        assertThat(storedQuery.getId(), is(id));
        assertThat(storedQuery.getName(), is(query));

        Optional<Failover> maybeFailover = storedQuery.getService().getFailover();
        assertTrue(maybeFailover.isPresent());

        var failover = maybeFailover.get();
        assertThat(failover.getNearestN(), is(Optional.of(3)));
        assertThat(failover.datacenters(), is(Optional.of(List.of("dc1", "dc2"))));
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

        assertThat(storedQueries.size(), is(2));

        List<String> queryIds = storedQueries.stream().map(StoredQuery::getId).collect(toList());
        assertThat(queryIds, hasItems(id1, id2));

        List<String> queryNames = storedQueries.stream().map(StoredQuery::getName).collect(toList());
        assertThat(queryNames, hasItems(query1, query2));
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
