package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_SECOND;
import static org.kiwiproject.consul.Awaiting.awaitWith25MsPoll;
import static org.kiwiproject.consul.TestUtils.randomUUIDString;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kiwiproject.consul.async.Callback;
import org.kiwiproject.consul.option.Options;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class SnapshotClientITest extends BaseIntegrationTest {

    private File snapshotFile;
    private SnapshotClient snapshotClient;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        snapshotClient = client.snapshotClient();
        snapshotFile = File.createTempFile("snapshot", ".gz", tempDir.toFile());
    }

    @Test
    void snapshotClientShouldBeAvailableInConsul() {
        assertThat(snapshotClient).isNotNull();
    }

    @Test
    void shouldBeAbleToSaveAndRestoreSnapshot() throws MalformedURLException, InterruptedException {
        var serviceName = randomUUIDString();
        var serviceId = randomUUIDString();

        var healthCheckUrl = URI.create("http://localhost:123/health").toURL();
        client.agentClient().register(8080, healthCheckUrl, 1000L, serviceName, serviceId,
                List.of(), Map.of());
        awaitWith25MsPoll().atMost(ONE_SECOND).until(() -> serviceExists(serviceName));

        ensureSaveSnapshot();

        client.agentClient().deregister(serviceId);
        awaitWith25MsPoll().atMost(ONE_SECOND).until(() -> !serviceExists(serviceName));

        ensureRestoreSnapshot();

        awaitWith25MsPoll().atMost(Duration.ofSeconds(30)).until(() -> {
            try {
                return serviceExists(serviceName);
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    private void ensureSaveSnapshot() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var success = new AtomicBoolean(false);
        snapshotClient.save(snapshotFile, Options.BLANK_QUERY_OPTIONS, createCallback(latch, success));
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(success.get()).isTrue();
    }

    private void ensureRestoreSnapshot() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var success = new AtomicBoolean(false);
        snapshotClient.restore(snapshotFile, Options.BLANK_QUERY_OPTIONS, createCallback(latch, success));
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(success.get()).isTrue();
    }

    private boolean serviceExists(String serviceName) {
        var serviceHealthList = client.healthClient().getAllServiceInstances(serviceName).getResponse();
        return !serviceHealthList.isEmpty();
    }

    private <T> Callback<T> createCallback(final CountDownLatch latch, final AtomicBoolean success) {
        return new Callback<>() {
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
