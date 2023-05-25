package com.orbitz.consul;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.Session;
import com.orbitz.consul.model.session.SessionCreatedResponse;
import com.orbitz.consul.model.session.SessionInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class SessionClientITest extends BaseIntegrationTest {

    private KeyValueClient keyValueClient;
    private SessionClient sessionClient;

    @BeforeEach
    void setUp() {
        keyValueClient = client.keyValueClient();
        sessionClient = client.sessionClient();
    }

    @Test
    void testCreateAndDestroySession() throws Exception {
        final Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        SessionCreatedResponse session = sessionClient.createSession(value);
        assertNotNull(session);

        sessionClient.destroySession(session.getId());
    }

    @Test
    void testCreateEmptySession() throws Exception {
        SessionCreatedResponse session = sessionClient.createSession(ImmutableSession.builder().build());
        assertNotNull(session);
        sessionClient.destroySession(session.getId());
    }

    @Test
    void shouldCreateSessionInDatacenter() {
        String datacenter = client.coordinateClient().getDatacenters().get(0).getDatacenter();

        ImmutableSession newSession = ImmutableSession.builder().build();
        SessionCreatedResponse sessionResponse = sessionClient.createSession(newSession, datacenter);
        assertNotNull(sessionResponse);
        sessionClient.destroySession(sessionResponse.getId());
    }

    @Test
    void testRenewSession() throws Exception {
        final Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();

        SessionCreatedResponse session = sessionClient.createSession(value);
        assertNotNull(session);

        try {
            SessionInfo info = sessionClient.renewSession(session.getId()).get();
            assertEquals(session.getId(), info.getId());
        } finally {
            sessionClient.destroySession(session.getId());
        }
    }

    @Test
    void testAcquireLock() {
        String key = randomUUIDString();

        Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(value).getId();
        String valueName = value.getName().get();

        try {
            assertTrue(keyValueClient.acquireLock(key, valueName, sessionId),
                    "Should succeed to acquire a lock");
            assertEquals(sessionId, keyValueClient.getSession(key).get());
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testAcquireLockTwiceFromSameSession() {
        String key = randomUUIDString();

        Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(value).getId();
        String valueName = value.getName().get();

        try {
            assertTrue(keyValueClient.acquireLock(key, valueName, sessionId),
                    "Should succeed to acquire a lock - first time");
            assertTrue(keyValueClient.acquireLock(key, valueName, sessionId),
                    "Should succeed to acquire a lock - second time");
            assertEquals(sessionId, keyValueClient.getSession(key).get());
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testAcquireLockTwiceFromDifferentSessions() {
        String key = randomUUIDString();

        Session firstSessionValue = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String firstSessionId = sessionClient.createSession(firstSessionValue).getId();
        String firstSessionValueContent = firstSessionValue.getName().get();

        Session secondSessionValue = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String secondSessionId = sessionClient.createSession(secondSessionValue).getId();
        String secondSessionValueNameContent = secondSessionValue.getName().get();

        try {
            assertTrue(keyValueClient.acquireLock(key, firstSessionValueContent, firstSessionId),
                    "Should succeed to acquire a lock - first session");
            assertFalse(keyValueClient.acquireLock(key, secondSessionValueNameContent, secondSessionId),
                    "Should fail to acquire a lock - second session");

            assertEquals(firstSessionId, keyValueClient.getSession(key).get());
        } finally {
            keyValueClient.releaseLock(key, firstSessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(firstSessionId);
            sessionClient.destroySession(secondSessionId);
        }
    }

    @Test
    void testGetSessionInfo() throws Exception {
        String key = randomUUIDString();

        Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(value).getId();
        String valueName = value.getName().get();

        try {
            assertTrue(keyValueClient.acquireLock(key, valueName, sessionId),
                    "Should succeed to acquire a lock");

            SessionInfo sessionInfo = sessionClient.getSessionInfo(sessionId).orElse(null);
            assertNotNull(sessionInfo);
            assertEquals(sessionId, sessionInfo.getId());
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testListSessions() throws Exception {
        String key = randomUUIDString();

        Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(value).getId();

        try {
            assertTrue(keyValueClient.acquireLock(key, value.getName().get(), sessionId));
            List<SessionInfo> result = sessionClient.listSessions();

            assertTrue(result.stream().anyMatch(sessionInfo -> sessionId.equals(sessionInfo.getId())));
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }
}
