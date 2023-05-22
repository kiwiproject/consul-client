package com.orbitz.consul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.orbitz.consul.model.query.Failover;
import com.orbitz.consul.model.query.ImmutableFailover;
import com.orbitz.consul.model.query.ImmutablePreparedQuery;
import com.orbitz.consul.model.query.ImmutableServiceQuery;
import com.orbitz.consul.model.query.PreparedQuery;
import com.orbitz.consul.model.query.StoredQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

public class PreparedQueryITest extends BaseIntegrationTest {

    private PreparedQueryClient preparedQueryClient;
    private List<String> queryIdsToDelete;

    @Before
    public void setUp() {
        preparedQueryClient = client.preparedQueryClient();
        queryIdsToDelete = new ArrayList<>();
    }

    @After
    public void tearDown() {
        queryIdsToDelete.forEach(id -> preparedQueryClient.deletePreparedQuery(id));
    }

    @Test
    public void shouldCreateAndFindPreparedQuery() {
        var serviceName = UUID.randomUUID().toString();
        var query = UUID.randomUUID().toString();
        client.agentClient().register(8080, 10000L, serviceName, serviceName + "1", Collections.emptyList(), Collections.emptyMap());

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
    public void shouldCreatePreparedQueryWithFailoverProperties() {
        var serviceName = UUID.randomUUID().toString();
        var query = UUID.randomUUID().toString();
        client.agentClient().register(8080, 10000L, serviceName, serviceName + "1", Collections.emptyList(), Collections.emptyMap());

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
    public void shouldListPreparedQueries() {
        var serviceName1 = UUID.randomUUID().toString();
        var query1 = UUID.randomUUID().toString();
        client.agentClient().register(8080, 10000L, serviceName1, serviceName1 + "_id", Collections.emptyList(), Collections.emptyMap());

        var serviceName2 = UUID.randomUUID().toString();
        var query2 = UUID.randomUUID().toString();
        client.agentClient().register(8080, 10000L, serviceName2, serviceName2 + "_id", Collections.emptyList(), Collections.emptyMap());

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
