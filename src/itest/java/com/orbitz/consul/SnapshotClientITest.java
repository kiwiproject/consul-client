package com.orbitz.consul;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.assertj.core.api.Assertions.assertThat;
import static com.orbitz.consul.Awaiting.awaitWith25MsPoll;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;
import com.orbitz.consul.async.Callback;
import com.orbitz.consul.option.QueryOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class SnapshotClientITest extends BaseIntegrationTest {

    private File snapshotFile;
    private SnapshotClient snapshotClient;

    @BeforeEach
    void setUp() throws IOException {
        snapshotClient = client.snapshotClient();
        snapshotFile = File.createTempFile("snapshot", ".gz");
    }

    @AfterEach
    void tearDown() {
        snapshotFile.delete();
    }

    @Test
    void snapshotClientShouldBeAvailableInConsul() {
        assertThat(snapshotClient).isNotNull();
    }

    @Test
    void shouldBeAbleToSaveAndRestoreSnapshot() throws MalformedURLException, InterruptedException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        client.agentClient().register(8080, new URL("http://localhost:123/health"), 1000L, serviceName, serviceId,
                List.of(), Map.of());
        awaitWith25MsPoll().atMost(TWO_HUNDRED_MILLISECONDS).until(() -> serviceExists(serviceName));

        ensureSaveSnapshot();

        client.agentClient().deregister(serviceId);
        awaitWith25MsPoll().atMost(TWO_HUNDRED_MILLISECONDS).until(() -> !serviceExists(serviceName));

        ensureRestoreSnapshot();

        await().atMost(FIVE_SECONDS).until(() -> serviceExists(serviceName));
    }

    private void ensureSaveSnapshot() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var success = new AtomicBoolean(false);
        snapshotClient.save(snapshotFile, QueryOptions.BLANK, createCallback(latch, success));
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(success.get()).isTrue();
    }

    private void ensureRestoreSnapshot() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var success = new AtomicBoolean(false);
        snapshotClient.restore(snapshotFile, QueryOptions.BLANK, createCallback(latch, success));
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(success.get()).isTrue();
    }

    private boolean serviceExists(String serviceName) {
        var serviceHealthList = client.healthClient().getAllServiceInstances(serviceName).getResponse();
        return !serviceHealthList.isEmpty();
    }

    private <T> Callback<T> createCallback(final CountDownLatch latch, final AtomicBoolean success) {
        return new Callback<T>() {
            @Override
            public void onResponse(T index) {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
                latch.countDown();
            }
        };
    }
}
