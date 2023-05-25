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
import com.orbitz.consul.monitoring.ClientEventCallback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import retrofit2.Retrofit;
import retrofit2.mock.BehaviorDelegate;
import retrofit2.mock.MockRetrofit;
import retrofit2.mock.NetworkBehavior;

class KVCacheTest {

    @ParameterizedTest(name = "wanted {0}, found {1}")
    @MethodSource("getKeyValueTestValues")
    void checkKeyExtractor(String rootPath, String input, String expected) {
        //Called in the constructor of the cache, must be use in the test as it may modify rootPath value.
        final String keyPath = KVCache.prepareRootPath(rootPath);

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
    void testListenerWithMockRetrofit() throws InterruptedException {
        final Retrofit retrofit = new Retrofit.Builder()
                // For safety, this is a black hole IP: see RFC 6666
                .baseUrl("http://[100:0:0:0:0:0:0:0]/")
                .build();
        final NetworkBehavior networkBehavior = NetworkBehavior.create();
        networkBehavior.setDelay(0, TimeUnit.MILLISECONDS);
        networkBehavior.setErrorPercent(0);
        networkBehavior.setFailurePercent(0);
        final MockRetrofit mockRetrofit = new MockRetrofit.Builder(retrofit)
                .networkBehavior(networkBehavior)
                .backgroundExecutor(Executors.newFixedThreadPool(1,
                        new ThreadFactoryBuilder()
                                .setNameFormat("mockRetrofitBackground-%d")
                                .build()))
                .build();

        final BehaviorDelegate<KeyValueClient.Api> delegate = mockRetrofit.create(KeyValueClient.Api.class);
        final MockApiService mockApiService = new MockApiService(delegate);


        final CacheConfig cacheConfig = CacheConfig.builder()
                .withMinDelayBetweenRequests(Duration.ofSeconds(10))
                .build();

        final KeyValueClient kvClient = KeyValueClientFactory.create(mockApiService, new ClientConfig(cacheConfig),
                new ClientEventCallback() {
        }, new Consul.NetworkTimeoutConfig.Builder().withReadTimeout(10500).build());


        try (final KVCache kvCache = KVCache.newCache(kvClient, "")) {
            kvCache.addListener(new AlwaysThrowsListener());
            final StubListener goodListener = new StubListener();
            kvCache.addListener(goodListener);

            kvCache.start();

            await().atMost(FIVE_SECONDS).until(() -> goodListener.getCallCount() > 0);

            assertThat(goodListener.getCallCount()).isEqualTo(1);
        }

    }
}