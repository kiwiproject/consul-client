package com.orbitz.consul.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.KeyValueClientFactory;
import com.orbitz.consul.MockApiService;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.config.ClientConfig;
import com.orbitz.consul.model.kv.ImmutableValue;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.monitoring.NoOpClientEventCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import retrofit2.Retrofit;
import retrofit2.mock.BehaviorDelegate;
import retrofit2.mock.MockRetrofit;
import retrofit2.mock.NetworkBehavior;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

class KVCacheTest {

    @ParameterizedTest(name = "wanted {0}, found {1}")
    @MethodSource("getKeyValueTestValues")
    void checkKeyExtractor(String rootPath, String input, String expected) {
        // Called in the constructor of the cache, must be used in the test as it may modify rootPath value.
        String keyPath = KVCache.prepareRootPath(rootPath);

        Function<Value, String> keyExtractor = KVCache.getKeyExtractorFunction(keyPath);
        assertThat(keyExtractor.apply(createValue(input))).isEqualTo(expected);
    }

    static Stream<Arguments> getKeyValueTestValues() {
        return Stream.of(
                arguments("", "a/b", "a/b"),
                arguments("/", "a/b", "a/b"),
                arguments("a", "a/b", "a/b"),
                arguments("a/", "a/b", "b"),
                arguments("a/b", "a/b", ""),
                arguments("a/b", "a/b/", "b/"),
                arguments("a/b", "a/b/c", "b/c"),
                arguments("a/b", "a/bc", "bc"),
                arguments("a/b/", "a/b/", ""),
                arguments("a/b/", "a/b/c", "c"),
                arguments("/a/b", "a/b", "")
        );
    }

    private Value createValue(final String key) {
        return ImmutableValue.builder()
                .createIndex(1234567890)
                .modifyIndex(1234567890)
                .lockIndex(1234567890)
                .flags(1234567890)
                .key(key)
                .value(Optional.empty())
                .build();
    }

    @Test
    void testListenerWithMockRetrofit() {
        var retrofit = new Retrofit.Builder()
                // For safety, this is a black hole IP: see RFC 6666
                .baseUrl("http://[100:0:0:0:0:0:0:0]/")
                .build();

        var networkBehavior = NetworkBehavior.create();
        networkBehavior.setDelay(0, TimeUnit.MILLISECONDS);
        networkBehavior.setErrorPercent(0);
        networkBehavior.setFailurePercent(0);

        var mockRetrofit = new MockRetrofit.Builder(retrofit)
                .networkBehavior(networkBehavior)
                .backgroundExecutor(Executors.newFixedThreadPool(1,
                        new ThreadFactoryBuilder()
                                .setNameFormat("mockRetrofitBackground-%d")
                                .build()))
                .build();

        BehaviorDelegate<KeyValueClient.Api> delegate = mockRetrofit.create(KeyValueClient.Api.class);
        var mockApiService = new MockApiService(delegate);


        var cacheConfig = CacheConfig.builder()
                .withMinDelayBetweenRequests(Duration.ofSeconds(10))
                .build();

        var keyValueClient = KeyValueClientFactory.create(
                mockApiService,
                new ClientConfig(cacheConfig),
                new NoOpClientEventCallback(),
                new Consul.NetworkTimeoutConfig.Builder().withReadTimeout(10500).build()
        );


        try (var kvCache = KVCache.newCache(keyValueClient, "")) {
            kvCache.addListener(new AlwaysThrowsListener());

            var goodListener = new StubListener();
            kvCache.addListener(goodListener);

            kvCache.start();

            await().atMost(FIVE_SECONDS).until(() -> goodListener.getCallCount() > 0);

            assertThat(goodListener.getCallCount()).isEqualTo(1);
        }

    }
}
