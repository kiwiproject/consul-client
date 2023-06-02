package com.orbitz.consul;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.assertj.core.api.Assertions.assertThat;

import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.ImmutableOperation;
import com.orbitz.consul.model.kv.Operation;
import com.orbitz.consul.model.kv.TxResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.SessionCreatedResponse;
import com.orbitz.consul.option.ConsistencyMode;
import com.orbitz.consul.option.ImmutableDeleteOptions;
import com.orbitz.consul.option.ImmutableDeleteOptions.Builder;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.PutOptions;
import com.orbitz.consul.option.QueryOptions;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class KeyValueITest extends BaseIntegrationTest {

    private static final Charset TEST_CHARSET = Charset.forName("IBM297");

    private KeyValueClient keyValueClient;
    private SessionClient sessionClient;

    @BeforeEach
    void setUp() {
        keyValueClient = client.keyValueClient();
        sessionClient = client.sessionClient();
    }

    @Test
    void shouldPutAndReceiveString() {
        var key = randomUUIDString();
        var value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value)).isTrue();
        assertThat(keyValueClient.getValueAsString(key)).contains(value);
    }

    @Test
    void shouldPutAndReceiveStringWithAnotherCharset() {
        var key = randomUUIDString();
        var value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, TEST_CHARSET)).isTrue();
        assertThat(keyValueClient.getValueAsString(key, TEST_CHARSET)).contains(value);
    }

    @Test
    void shouldPutAndReceiveValue() {
        var key = randomUUIDString();
        var value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value)).isTrue();
        Value received = keyValueClient.getValue(key).orElseThrow();
        assertThat(received.getValueAsString()).contains(value);
        assertThat(received.getFlags()).isZero();
    }

    @Test
    void shouldPutAndReceiveValueWithAnotherCharset() {
        var key = randomUUIDString();
        var value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, TEST_CHARSET)).isTrue();
        Value received = keyValueClient.getValue(key).orElseThrow();
        assertThat(received.getValueAsString(TEST_CHARSET)).contains(value);
        assertThat(received.getFlags()).isZero();
    }

    @Test
    void shouldPutAndReceiveWithFlags() {
        var key = randomUUIDString();
        var value = randomUUIDString();
        long flags = UUID.randomUUID().getMostSignificantBits();

        assertThat(keyValueClient.putValue(key, value, flags)).isTrue();
        Value received = keyValueClient.getValue(key).orElseThrow();
        assertThat(received.getValueAsString()).contains(value);
        assertThat(received.getFlags()).isEqualTo(flags);
    }

    @Test
    void shouldPutAndReceiveWithFlagsAndCharset() {
        var key = randomUUIDString();
        var value = randomUUIDString();
        long flags = UUID.randomUUID().getMostSignificantBits();

        assertThat(keyValueClient.putValue(key, value, flags, TEST_CHARSET)).isTrue();
        Value received = keyValueClient.getValue(key).orElseThrow();
        assertThat(received.getValueAsString(TEST_CHARSET)).contains(value);
        assertThat(received.getFlags()).isEqualTo(flags);
    }

    @Test
    void putNullValue() {
        var key = randomUUIDString();

        assertThat(keyValueClient.putValue(key)).isTrue();

        Value received = keyValueClient.getValue(key).orElseThrow();
        assertThat(received.getValue()).isEmpty();
    }

    @Test
    void putNullValueWithAnotherCharset() {
        var key = randomUUIDString();

        assertThat(keyValueClient.putValue(key, null, 0, PutOptions.BLANK, TEST_CHARSET)).isTrue();

        Value received = keyValueClient.getValue(key).orElseThrow();
        assertThat(received.getValue()).isEmpty();
    }

    @Test
    void shouldPutAndReceiveBytes() {
        byte[] value = new byte[256];
        ThreadLocalRandom.current().nextBytes(value);

        var key = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, 0, PutOptions.BLANK)).isTrue();

        Value received = keyValueClient.getValue(key).orElseThrow();
        assertThat(received.getValue()).isPresent();

        byte[] receivedBytes = received.getValueAsBytes().orElseThrow();

        assertThat(receivedBytes).containsExactly(value);
    }

    @Test
    void shouldPutAndReceiveStrings() {
        var key = randomUUIDString();
        var key2 = key + "/" + randomUUIDString();
        var value = randomUUIDString();
        var value2 = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value)).isTrue();
        assertThat(keyValueClient.putValue(key2, value2)).isTrue();
        assertThat(new HashSet<>(keyValueClient.getValuesAsString(key))).isEqualTo(Set.of(value, value2));
    }

    @Test
    void shouldPutAndReceiveStringsWithAnotherCharset() {
        var key = randomUUIDString();
        var key2 = key + "/" + randomUUIDString();
        var value = randomUUIDString();
        var value2 = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, TEST_CHARSET)).isTrue();
        assertThat(keyValueClient.putValue(key2, value2, TEST_CHARSET)).isTrue();
        assertThat(new HashSet<>(keyValueClient.getValuesAsString(key, TEST_CHARSET))).isEqualTo(Set.of(value, value2));
    }

    @Test
    void shouldDelete() {
        var key = randomUUIDString();
        var value = randomUUIDString();

        keyValueClient.putValue(key, value);
        assertThat(keyValueClient.getValueAsString(key)).isPresent();

        keyValueClient.deleteKey(key);
        assertThat(keyValueClient.getValueAsString(key)).isEmpty();
    }


    @Test
    void shouldDeleteRecursively() {
        var key = randomUUIDString();
        var childKEY = key + "/" + randomUUIDString();

        var value = randomUUIDString();

        keyValueClient.putValue(key);
        keyValueClient.putValue(childKEY, value);

        assertThat(keyValueClient.getValueAsString(childKEY)).isPresent();

        keyValueClient.deleteKeys(key);

        assertThat(keyValueClient.getValueAsString(key)).isEmpty();
        assertThat(keyValueClient.getValueAsString(childKEY)).isEmpty();
    }

    @Test
    void shouldDeleteCas() {
        var key = randomUUIDString();
        var value = randomUUIDString();

        // Update the value twice and remember the value at each step
        keyValueClient.putValue(key, value);

        final Optional<Value> valueAfter1stPut = keyValueClient.getValue(key);
        assertThat(valueAfter1stPut).isPresent();
        assertThat(valueAfter1stPut.get().getValueAsString()).isPresent();

        keyValueClient.putValue(key, randomUUIDString());

        final Optional<Value> valueAfter2ndPut = keyValueClient.getValue(key);
        assertThat(valueAfter2ndPut).isPresent();
        assertThat(valueAfter2ndPut.get().getValueAsString()).isPresent();

        // Trying to delete the key once with the older lock, which should not work
        final Builder deleteOptionsBuilderWithOlderLock = ImmutableDeleteOptions.builder();
        deleteOptionsBuilderWithOlderLock.cas(valueAfter1stPut.get().getModifyIndex());
        final ImmutableDeleteOptions deleteOptionsWithOlderLock = deleteOptionsBuilderWithOlderLock.build();

        keyValueClient.deleteKey(key, deleteOptionsWithOlderLock);

        assertThat(keyValueClient.getValueAsString(key)).isPresent();

        // Deleting the key with the most recent lock, which should work
        final Builder deleteOptionsBuilderWithLatestLock = ImmutableDeleteOptions.builder();
        deleteOptionsBuilderWithLatestLock.cas(valueAfter2ndPut.get().getModifyIndex());
        final ImmutableDeleteOptions deleteOptionsWithLatestLock = deleteOptionsBuilderWithLatestLock.build();

        keyValueClient.deleteKey(key, deleteOptionsWithLatestLock);

        assertThat(keyValueClient.getValueAsString(key)).isEmpty();
    }

    @Test
    void acquireAndReleaseLock() {
        var key = randomUUIDString();
        var value = "session_" + randomUUIDString();

        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(value).build());
        String sessionId = response.getId();

        try {
            assertThat(keyValueClient.acquireLock(key, value, sessionId)).isTrue();
            assertThat(keyValueClient.acquireLock(key, value, sessionId)).isTrue(); // No ideas why there was an assertFalse

            var valueAfterAcquireLock = keyValueClient.getValue(key).orElseThrow();
            assertThat(valueAfterAcquireLock.getSession()).as("SessionId must be present.").isPresent();

            assertThat(keyValueClient.releaseLock(key, sessionId)).isTrue();

            var valueAfterReleaseLock = keyValueClient.getValue(key).orElseThrow();
            assertThat(valueAfterReleaseLock.getSession()).as("SessionId in the key value should be absent.").isEmpty();

            keyValueClient.deleteKey(key);
        } finally {
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testGetSession() {
        var key = randomUUIDString();
        var value = randomUUIDString();
        keyValueClient.putValue(key, value);

        assertThat(keyValueClient.getSession(key)).isEmpty();

        var sessionValue = "session_" + randomUUIDString();
        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(sessionValue).build());
        String sessionId = response.getId();

        try {
            assertThat(keyValueClient.acquireLock(key, sessionValue, sessionId)).isTrue();
            assertThat(keyValueClient.acquireLock(key, sessionValue, sessionId)).isTrue(); // No ideas why there was an assertFalse

            var sessionString = keyValueClient.getSession(key).orElseThrow();
            assertThat(sessionString).isEqualTo(sessionId);
        } finally {
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testGetKeys() {
        var testString = "Hello World!";
        var key = "my_key";
        keyValueClient.putValue(key, testString);
        List<String> list = keyValueClient.getKeys(key);

        assertThat(list).isNotEmpty();
        assertThat(list.get(0)).isEqualTo(key);
    }

    @Test
    void testAcquireLock() {
        var key = randomUUIDString();
        var value = randomUUIDString();
        keyValueClient.putValue(key, value);

        assertThat(keyValueClient.getSession(key)).isEmpty();

        String sessionValue = "session_" + randomUUIDString();
        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(sessionValue).build());
        String sessionId = response.getId();

        String sessionValue2 = "session_" + randomUUIDString();
        SessionCreatedResponse response2 = sessionClient.createSession(ImmutableSession.builder().name(sessionValue).build());
        String sessionId2 = response2.getId();

        try {
            assertThat(keyValueClient.acquireLock(key, sessionValue, sessionId)).isTrue();
            // session-2 can't acquire the lock
            assertThat(keyValueClient.acquireLock(key, sessionValue2, sessionId2)).isFalse();
            assertThat(keyValueClient.getSession(key)).contains(sessionId);

            keyValueClient.releaseLock(key, sessionId);

            // session-2 now can acquire the lock
            assertThat(keyValueClient.acquireLock(key, sessionValue2, sessionId2)).isTrue();
            // session-1 can't acquire the lock anymore
            assertThat(keyValueClient.acquireLock(key, sessionValue, sessionId)).isFalse();
            assertThat(keyValueClient.getSession(key)).contains(sessionId2);
        } finally {
            sessionClient.destroySession(sessionId);
            sessionClient.destroySession(sessionId2);
        }
    }

    @Test
    void testGetValuesAsync() throws InterruptedException {
        var key = randomUUIDString();
        var value = randomUUIDString();
        keyValueClient.putValue(key, value);

        final var completed = new CountDownLatch(1);
        final var success = new AtomicBoolean(false);

        keyValueClient.getValues(key, QueryOptions.BLANK, new ConsulResponseCallback<>() {
            @Override
            public void onComplete(ConsulResponse<List<Value>> consulResponse) {
                success.set(true);
                completed.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                completed.countDown();
            }
        });
        var countReachedZero = completed.await(3, TimeUnit.SECONDS);
        assertThat(countReachedZero).isTrue();

        keyValueClient.deleteKey(key);
        assertThat(success.get()).isTrue();
    }

    @Test
    void testGetConsulResponseWithValue() {
        var key = randomUUIDString();
        var value = randomUUIDString();
        keyValueClient.putValue(key, value);

        Optional<ConsulResponse<Value>> responseOptional = keyValueClient.getConsulResponseWithValue(key);

        keyValueClient.deleteKey(key);

        assertThat(responseOptional).isPresent();
        ConsulResponse<Value> consulResponse = responseOptional.get();
        assertThat(consulResponse.getResponse().getKey()).isEqualTo(key);
        assertThat(consulResponse.getResponse().getValue()).isPresent();
        assertThat(consulResponse.getIndex()).isNotNull();
    }

    @Test
    void testGetConsulResponseWithValue_WhenNotFound() {
        var key = randomUUIDString();

        Optional<ConsulResponse<Value>> responseOptional = keyValueClient.getConsulResponseWithValue(key);

        assertThat(responseOptional).isEmpty();
    }

    @Test
    void testGetConsulResponseWithValues() {
        var key = randomUUIDString();
        var value = randomUUIDString();
        keyValueClient.putValue(key, value);

        ConsulResponse<List<Value>> response = keyValueClient.getConsulResponseWithValues(key);

        keyValueClient.deleteKey(key);

        assertThat(!response.getResponse().isEmpty()).isTrue();
        assertThat(response.getIndex()).isNotNull();
    }

    @Test
    void testGetValueNotFoundAsync() throws InterruptedException {
        var key = randomUUIDString();

        final var numTests = 2;
        final var completed = new CountDownLatch(numTests);
        final var success = new AtomicInteger(0);

        keyValueClient.getValue(key, QueryOptions.BLANK, new ConsulResponseCallback<>() {

            @Override
            public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {
                assertThat(consulResponse).isNotNull();
                // No cache, no Cache info
                assertThat(consulResponse.getCacheReponseInfo()).isEmpty();
                success.incrementAndGet();
                completed.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new AssertionError("KV should work without cache, 404 is not an error", throwable);
            }
        });

        var countIsZeroAfterFirstGetValue = completed.await(2, TimeUnit.SECONDS);
        assertThat(countIsZeroAfterFirstGetValue)
                .describedAs("Count should not have reached zero after first call to getValue")
                .isFalse();
        assertThat(completed.getCount()).isOne();

        var maxAgeInSeconds = Optional.of(60L);
        var maxStaleInSeconds = Optional.of(180L);
        var consistencyMode = ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(maxAgeInSeconds, maxStaleInSeconds);
        var queryOptions = ImmutableQueryOptions.builder()
                .consistencyMode(consistencyMode)
                .build();

        keyValueClient.getValue(key, queryOptions, new ConsulResponseCallback<>() {

            @Override
            public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {
                assertThat(consulResponse).isNotNull();
                success.incrementAndGet();
                completed.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new AssertionError("KV should work with cache even if cache does not support ?cached", throwable);
            }
        });

        var countIsZeroAfterSecondGetValue = completed.await(2, TimeUnit.SECONDS);
        keyValueClient.deleteKey(key);
        assertThat(countIsZeroAfterSecondGetValue)
                .describedAs("Count should have reached zero after second call to getValue")
                .isTrue();
        assertThat(numTests).as("Should be all success").isEqualTo(success.get());
    }

    @Test
    void testBasicTxn() {
        var key = randomUUIDString();
        var value = Base64.encodeBase64String(RandomStringUtils.random(20).getBytes());
        Operation[] operation = new Operation[] { ImmutableOperation.builder().verb("set")
                .key(key)
                .value(value).build() };

        ConsulResponse<TxResponse> response = keyValueClient.performTransaction(operation);

        assertThat(keyValueClient.getValueAsString(key)).contains(value);
        assertThat(response.getResponse().results().get(0).get("KV").getKey()).isEqualTo(key);
    }

    @SuppressWarnings("removal")
    @Test
    void testBasicTxn_Deprecated_Using_DEFAULT_ConsistencyMode() {
        var key = randomUUIDString();
        var value = Base64.encodeBase64String(RandomStringUtils.random(20).getBytes());
        Operation[] operation = new Operation[] { ImmutableOperation.builder().verb("set")
                .key(key)
                .value(value).build() };

        ConsulResponse<TxResponse> response = keyValueClient.performTransaction(ConsistencyMode.DEFAULT, operation);

        assertThat(keyValueClient.getValueAsString(key)).contains(value);
        assertThat(response.getResponse().results().get(0).get("KV").getKey()).isEqualTo(key);
    }

    @SuppressWarnings("removal")
    @Test
    void testBasicTxn_Deprecated_Using_CONSISTENT_ConsistencyMode() {
        var key = randomUUIDString();
        var value = Base64.encodeBase64String(RandomStringUtils.random(20).getBytes());
        Operation[] operation = new Operation[] { ImmutableOperation.builder().verb("set")
                .key(key)
                .value(value).build() };

        ConsulResponse<TxResponse> response = keyValueClient.performTransaction(ConsistencyMode.CONSISTENT, operation);

        assertThat(keyValueClient.getValueAsString(key)).contains(value);
        assertThat(response.getResponse().results().get(0).get("KV").getKey()).isEqualTo(key);
    }

    @Test
    void testGetKeysWithSeparator() {
        keyValueClient.putValue("nested/first", "first");
        keyValueClient.putValue("nested/second", "second");

        List<String> keys = keyValueClient.getKeys("nested", "/");
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0)).isEqualTo("nested/");
    }

    @Test
    void testUnknownKeyGetValues() {
        List<String> shouldBeEmpty = keyValueClient.getValuesAsString("unknownKey");
        assertThat(shouldBeEmpty).isEmpty();
    }

    @Test
    void testUnknownKeyGetKeys() {
        List<String> shouldBeEmpty = keyValueClient.getKeys("unknownKey");
        assertThat(shouldBeEmpty).isEmpty();
    }

    @Test
    void shouldReturnEmptyOptional_WhenGetValue_WithUnknownKey() {
        var valueOptional = keyValueClient.getValue("unknownKey", QueryOptions.BLANK);
        assertThat(valueOptional).isEmpty();
    }

    @Test
    void shouldReturnEmptyOptional_WhenGetConsulResponseWithValue_WithUnknownKey() {
        var consulResponseOptional = keyValueClient.getConsulResponseWithValue("unknownKey", QueryOptions.BLANK);
        assertThat(consulResponseOptional).isEmpty();
    }
}
