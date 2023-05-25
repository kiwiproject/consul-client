package com.orbitz.consul;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
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

    @Test
    void shouldPutAndReceiveString() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value)).isTrue();
        assertThat(keyValueClient.getValueAsString(key).get()).isEqualTo(value);
    }

    @Test
    void shouldPutAndReceiveStringWithAnotherCharset() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, TEST_CHARSET)).isTrue();
        assertThat(keyValueClient.getValueAsString(key, TEST_CHARSET).get()).isEqualTo(value);
    }

    @Test
    void shouldPutAndReceiveValue() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value)).isTrue();
        Value received = keyValueClient.getValue(key).get();
        assertThat(received.getValueAsString().get()).isEqualTo(value);
        assertThat(received.getFlags()).isEqualTo(0L);
    }

    @Test
    void shouldPutAndReceiveValueWithAnotherCharset() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, TEST_CHARSET)).isTrue();
        Value received = keyValueClient.getValue(key).get();
        assertThat(received.getValueAsString(TEST_CHARSET).get()).isEqualTo(value);
        assertThat(received.getFlags()).isEqualTo(0L);
    }

    @Test
    void shouldPutAndReceiveWithFlags() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();
        long flags = UUID.randomUUID().getMostSignificantBits();

        assertThat(keyValueClient.putValue(key, value, flags)).isTrue();
        Value received = keyValueClient.getValue(key).get();
        assertThat(received.getValueAsString().get()).isEqualTo(value);
        assertThat(received.getFlags()).isEqualTo(flags);
    }

    @Test
    void shouldPutAndReceiveWithFlagsAndCharset() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();
        long flags = UUID.randomUUID().getMostSignificantBits();

        assertThat(keyValueClient.putValue(key, value, flags, TEST_CHARSET)).isTrue();
        Value received = keyValueClient.getValue(key).get();
        assertThat(received.getValueAsString(TEST_CHARSET).get()).isEqualTo(value);
        assertThat(received.getFlags()).isEqualTo(flags);
    }

    @Test
    void putNullValue() {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();

        assertThat(keyValueClient.putValue(key)).isTrue();

        Value received = keyValueClient.getValue(key).get();
        assertThat(received.getValue().isPresent()).isFalse();
    }

    @Test
    void putNullValueWithAnotherCharset() {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();

        assertThat(keyValueClient.putValue(key, null, 0, PutOptions.BLANK, TEST_CHARSET)).isTrue();

        Value received = keyValueClient.getValue(key).get();
        assertThat(received.getValue().isPresent()).isFalse();
    }

    @Test
    void shouldPutAndReceiveBytes() {
        KeyValueClient keyValueClient = client.keyValueClient();
        byte[] value = new byte[256];
        ThreadLocalRandom.current().nextBytes(value);

        String key = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, 0, PutOptions.BLANK)).isTrue();

        Value received = keyValueClient.getValue(key).get();
        assertThat(received.getValue().isPresent()).isTrue();

        byte[] receivedBytes = received.getValueAsBytes()
                .get();

        assertThat(receivedBytes).containsExactly(value);
    }

    @Test
    void shouldPutAndReceiveStrings() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String key2 = key + "/" + randomUUIDString();
        final String value = randomUUIDString();
        final String value2 = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value)).isTrue();
        assertThat(keyValueClient.putValue(key2, value2)).isTrue();
        assertThat(new HashSet<>(keyValueClient.getValuesAsString(key))).isEqualTo(Set.of(value, value2));
    }

    @Test
    void shouldPutAndReceiveStringsWithAnotherCharset() throws UnknownHostException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String key2 = key + "/" + randomUUIDString();
        final String value = randomUUIDString();
        final String value2 = randomUUIDString();

        assertThat(keyValueClient.putValue(key, value, TEST_CHARSET)).isTrue();
        assertThat(keyValueClient.putValue(key2, value2, TEST_CHARSET)).isTrue();
        assertThat(new HashSet<>(keyValueClient.getValuesAsString(key, TEST_CHARSET))).isEqualTo(Set.of(value, value2));
    }

    @Test
    void shouldDelete() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        final String value = randomUUIDString();

        keyValueClient.putValue(key, value);
        assertThat(keyValueClient.getValueAsString(key).isPresent()).isTrue();

        keyValueClient.deleteKey(key);
        assertThat(keyValueClient.getValueAsString(key).isPresent()).isFalse();
    }


    @Test
    void shouldDeleteRecursively() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String childKEY = key + "/" + randomUUIDString();

        final String value = randomUUIDString();

        keyValueClient.putValue(key);
        keyValueClient.putValue(childKEY, value);

        assertThat(keyValueClient.getValueAsString(childKEY).isPresent()).isTrue();

        keyValueClient.deleteKeys(key);

        assertThat(keyValueClient.getValueAsString(key).isPresent()).isFalse();
        assertThat(keyValueClient.getValueAsString(childKEY).isPresent()).isFalse();
    }

    @Test
    void shouldDeleteCas() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        final String value = randomUUIDString();

        /**
         * Update the value twice and remember the value at each step
         */
        keyValueClient.putValue(key, value);

        final Optional<Value> valueAfter1stPut = keyValueClient.getValue(key);
        assertThat(valueAfter1stPut.isPresent()).isTrue();
        assertThat(valueAfter1stPut.get().getValueAsString().isPresent()).isTrue();

        keyValueClient.putValue(key, randomUUIDString());

        final Optional<Value> valueAfter2ndPut = keyValueClient.getValue(key);
        assertThat(valueAfter2ndPut.isPresent()).isTrue();
        assertThat(valueAfter2ndPut.get().getValueAsString().isPresent()).isTrue();

        /**
         * Trying to delete the key once with the older lock, which should not work
         */
        final Builder deleteOptionsBuilderWithOlderLock = ImmutableDeleteOptions.builder();
        deleteOptionsBuilderWithOlderLock.cas(valueAfter1stPut.get().getModifyIndex());
        final ImmutableDeleteOptions deleteOptionsWithOlderLock = deleteOptionsBuilderWithOlderLock.build();

        keyValueClient.deleteKey(key, deleteOptionsWithOlderLock);

        assertThat(keyValueClient.getValueAsString(key).isPresent()).isTrue();

        /**
         * Deleting the key with the most recent lock, which should work
         */
        final Builder deleteOptionsBuilderWithLatestLock = ImmutableDeleteOptions.builder();
        deleteOptionsBuilderWithLatestLock.cas(valueAfter2ndPut.get().getModifyIndex());
        final ImmutableDeleteOptions deleteOptionsWithLatestLock = deleteOptionsBuilderWithLatestLock.build();

        keyValueClient.deleteKey(key, deleteOptionsWithLatestLock);

        assertThat(keyValueClient.getValueAsString(key).isPresent()).isFalse();
    }

    @Test
    void acquireAndReleaseLock() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        SessionClient sessionClient = client.sessionClient();
        String key = randomUUIDString();
        String value = "session_" + randomUUIDString();
        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(value).build());
        String sessionId = response.getId();

        try {
            assertThat(keyValueClient.acquireLock(key, value, sessionId)).isTrue();
            assertThat(keyValueClient.acquireLock(key, value, sessionId)).isTrue(); // No ideas why there was an assertFalse

            assertThat(keyValueClient.getValue(key).get().getSession().isPresent()).as("SessionId must be present.").isTrue();
            assertThat(keyValueClient.releaseLock(key, sessionId)).isTrue();
            assertThat(keyValueClient.getValue(key).get().getSession().isPresent()).as("SessionId in the key value should be absent.").isFalse();
            keyValueClient.deleteKey(key);
        } finally {
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testGetSession() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        SessionClient sessionClient = client.sessionClient();

        String key = randomUUIDString();
        String value = randomUUIDString();
        keyValueClient.putValue(key, value);

        assertThat(keyValueClient.getSession(key).isPresent()).isEqualTo(false);

        String sessionValue = "session_" + randomUUIDString();
        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(sessionValue).build());
        String sessionId = response.getId();

        try {
            assertThat(keyValueClient.acquireLock(key, sessionValue, sessionId)).isTrue();
            assertThat(keyValueClient.acquireLock(key, sessionValue, sessionId)).isTrue(); // No ideas why there was an assertFalse
            assertThat(keyValueClient.getSession(key).get()).isEqualTo(sessionId);
        } finally {
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testGetKeys() throws Exception {
        KeyValueClient kvClient = client.keyValueClient();
        String testString = "Hello World!";
        String key = "my_key";
        kvClient.putValue(key, testString);
        List<String> list = kvClient.getKeys(key);

        assertThat(list.isEmpty()).isFalse();
        assertThat(list.get(0)).isEqualTo(key);
    }

    @Test
    void testAcquireLock() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        SessionClient sessionClient = client.sessionClient();

        String key = randomUUIDString();
        String value = randomUUIDString();
        keyValueClient.putValue(key, value);

        assertThat(keyValueClient.getSession(key).isPresent()).isEqualTo(false);

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
            assertThat(keyValueClient.getSession(key).get()).isEqualTo(sessionId);

            keyValueClient.releaseLock(key, sessionId);

            // session-2 now can acquire the lock
            assertThat(keyValueClient.acquireLock(key, sessionValue2, sessionId2)).isTrue();
            // session-1 can't acquire the lock anymore
            assertThat(keyValueClient.acquireLock(key, sessionValue, sessionId)).isFalse();
            assertThat(keyValueClient.getSession(key).get()).isEqualTo(sessionId2);
        } finally {
            sessionClient.destroySession(sessionId);
            sessionClient.destroySession(sessionId2);
        }
    }

    @Test
    void testGetValuesAsync() throws InterruptedException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();
        keyValueClient.putValue(key, value);

        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        keyValueClient.getValues(key, QueryOptions.BLANK, new ConsulResponseCallback<List<Value>>() {
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
        completed.await(3, TimeUnit.SECONDS);
        keyValueClient.deleteKey(key);
        assertThat(success.get()).isTrue();
    }

    @Test
    void testGetConsulResponseWithValue() {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();
        keyValueClient.putValue(key, value);

        Optional<ConsulResponse<Value>> response = keyValueClient.getConsulResponseWithValue(key);

        keyValueClient.deleteKey(key);

        assertThat(response.get().getResponse().getKey()).isEqualTo(key);
        assertThat(response.get().getResponse().getValue().isPresent()).isTrue();
        assertThat(response.get().getIndex()).isNotNull();
    }

    @Test
    void testGetConsulResponseWithValues() {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = randomUUIDString();
        keyValueClient.putValue(key, value);

        ConsulResponse<List<Value>> response = keyValueClient.getConsulResponseWithValues(key);

        keyValueClient.deleteKey(key);

        assertThat(!response.getResponse().isEmpty()).isTrue();
        assertThat(response.getIndex()).isNotNull();
    }

    @Test
    void testGetValueNotFoundAsync() throws InterruptedException {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();

        final int numTests = 2;
        final CountDownLatch completed = new CountDownLatch(numTests);
        final AtomicInteger success = new AtomicInteger(0);

        keyValueClient.getValue(key, QueryOptions.BLANK, new ConsulResponseCallback<Optional<Value>>() {

            @Override
            public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {
                assertNotNull(consulResponse);
                // No cache, no Cache info
                assertFalse(consulResponse.getCacheReponseInfo().isPresent());
                completed.countDown();
                success.incrementAndGet();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new AssertionError("KV should work without cache, 404 is not an error", throwable);
            }
        });
        completed.await(3, TimeUnit.SECONDS);
        QueryOptions queryOptions = ImmutableQueryOptions.builder()
                                      .consistencyMode(ConsistencyMode.createCachedConsistencyWithMaxAgeAndStale(Optional.of(60L), Optional.of(180L))).build();
        keyValueClient.getValue(key, queryOptions, new ConsulResponseCallback<Optional<Value>>() {

            @Override
            public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {
                assertNotNull(consulResponse);
                completed.countDown();
                success.incrementAndGet();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new AssertionError("KV should work with cache even if cache does not support ?cached", throwable);
            }
        });

        completed.await(3, TimeUnit.SECONDS);
        keyValueClient.deleteKey(key);
        assertThat(numTests).as("Should be all success").isEqualTo(success.get());
    }

    @Test
    void testBasicTxn() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = Base64.encodeBase64String(RandomStringUtils.random(20).getBytes());
        Operation[] operation = new Operation[] {ImmutableOperation.builder().verb("set")
                .key(key)
                .value(value).build()};

        ConsulResponse<TxResponse> response = keyValueClient.performTransaction(operation);

        assertThat(keyValueClient.getValueAsString(key).get()).isEqualTo(value);
        assertThat(response.getResponse().results().get(0).get("KV").getKey()).isEqualTo(key);
    }

    @Test
    void testBasicTxn_Deprecated_Using_DEFAULT_ConsistencyMode() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = Base64.encodeBase64String(RandomStringUtils.random(20).getBytes());
        Operation[] operation = new Operation[] {ImmutableOperation.builder().verb("set")
                .key(key)
                .value(value).build()};

        ConsulResponse<TxResponse> response = keyValueClient.performTransaction(ConsistencyMode.DEFAULT, operation);

        assertThat(keyValueClient.getValueAsString(key).get()).isEqualTo(value);
        assertThat(response.getResponse().results().get(0).get("KV").getKey()).isEqualTo(key);
    }

    @Test
    void testBasicTxn_Deprecated_Using_CONSISTENT_ConsistencyMode() throws Exception {
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = randomUUIDString();
        String value = Base64.encodeBase64String(RandomStringUtils.random(20).getBytes());
        Operation[] operation = new Operation[] {ImmutableOperation.builder().verb("set")
                .key(key)
                .value(value).build()};

        ConsulResponse<TxResponse> response = keyValueClient.performTransaction(ConsistencyMode.CONSISTENT, operation);

        assertThat(keyValueClient.getValueAsString(key).get()).isEqualTo(value);
        assertThat(response.getResponse().results().get(0).get("KV").getKey()).isEqualTo(key);
    }

    @Test
    void testGetKeysWithSeparator() {
        KeyValueClient kvClient = client.keyValueClient();
        kvClient.putValue("nested/first", "first");
        kvClient.putValue("nested/second", "second");

        List<String> keys = kvClient.getKeys("nested", "/");
        assertThat(keys.size()).isEqualTo(1);
        assertThat(keys.get(0)).isEqualTo("nested/");
    }

    @Test
    void testUnknownKeyGetValues() {
        List<String> shouldBeEmpty = client.keyValueClient().getValuesAsString("unknownKey");
        assertThat(shouldBeEmpty.isEmpty()).isTrue();
    }

    @Test
    void testUnknownKeyGetKeys() {
        List<String> shouldBeEmpty = client.keyValueClient().getKeys("unknownKey");
        assertThat(shouldBeEmpty.isEmpty()).isTrue();
    }
}
