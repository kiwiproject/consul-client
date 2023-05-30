package com.orbitz.consul;

import static com.orbitz.consul.TestUtils.randomUUIDString;
import static org.assertj.core.api.Assertions.assertThat;

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
    void testCreateAndDestroySession() {
        final Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        SessionCreatedResponse session = sessionClient.createSession(value);
        assertThat(session).isNotNull();

        sessionClient.destroySession(session.getId());
    }

    @Test
    void testCreateEmptySession() {
        SessionCreatedResponse session = sessionClient.createSession(ImmutableSession.builder().build());
        assertThat(session).isNotNull();
        sessionClient.destroySession(session.getId());
    }

    @Test
    void shouldCreateSessionInDatacenter() {
        String datacenter = client.coordinateClient().getDatacenters().get(0).getDatacenter();

        ImmutableSession newSession = ImmutableSession.builder().build();
        SessionCreatedResponse sessionResponse = sessionClient.createSession(newSession, datacenter);
        assertThat(sessionResponse).isNotNull();
        sessionClient.destroySession(sessionResponse.getId());
    }

    @Test
    void testRenewSession() {
        final Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();

        SessionCreatedResponse session = sessionClient.createSession(value);
        assertThat(session).isNotNull();

        try {
            SessionInfo info = sessionClient.renewSession(session.getId()).orElseThrow();
            assertThat(info.getId()).isEqualTo(session.getId());
        } finally {
            sessionClient.destroySession(session.getId());
        }
    }

    @Test
    void testAcquireLock() {
        var key = randomUUIDString();

        Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(value).getId();
        String valueName = value.getName().orElseThrow();

        try {
            assertThat(keyValueClient.acquireLock(key, valueName, sessionId)).as("Should succeed to acquire a lock").isTrue();
            assertThat(keyValueClient.getSession(key)).contains(sessionId);
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testAcquireLockTwiceFromSameSession() {
        var key = randomUUIDString();

        Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(value).getId();
        String valueName = value.getName().orElseThrow();

        try {
            assertThat(keyValueClient.acquireLock(key, valueName, sessionId)).as("Should succeed to acquire a lock - first time").isTrue();
            assertThat(keyValueClient.acquireLock(key, valueName, sessionId)).as("Should succeed to acquire a lock - second time").isTrue();
            assertThat(keyValueClient.getSession(key)).contains(sessionId);
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testAcquireLockTwiceFromDifferentSessions() {
        var key = randomUUIDString();

        Session firstSessionValue = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String firstSessionId = sessionClient.createSession(firstSessionValue).getId();
        String firstSessionValueContent = firstSessionValue.getName().orElseThrow();

        Session secondSessionValue = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String secondSessionId = sessionClient.createSession(secondSessionValue).getId();
        String secondSessionValueNameContent = secondSessionValue.getName().orElseThrow();

        try {
            assertThat(keyValueClient.acquireLock(key, firstSessionValueContent, firstSessionId)).as("Should succeed to acquire a lock - first session").isTrue();
            assertThat(keyValueClient.acquireLock(key, secondSessionValueNameContent, secondSessionId)).as("Should fail to acquire a lock - second session").isFalse();

            assertThat(keyValueClient.getSession(key)).contains(firstSessionId);
        } finally {
            keyValueClient.releaseLock(key, firstSessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(firstSessionId);
            sessionClient.destroySession(secondSessionId);
        }
    }

    @Test
    void testGetSessionInfo() {
        var key = randomUUIDString();

        Session value = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(value).getId();
        String valueName = value.getName().orElseThrow();

        try {
            assertThat(keyValueClient.acquireLock(key, valueName, sessionId)).as("Should succeed to acquire a lock").isTrue();

            SessionInfo sessionInfo = sessionClient.getSessionInfo(sessionId).orElse(null);
            assertThat(sessionInfo).isNotNull();
            assertThat(sessionInfo.getId()).isEqualTo(sessionId);
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }

    @Test
    void testListSessions() {
        var key = randomUUIDString();

        Session session = ImmutableSession.builder().name("session_" + randomUUIDString()).build();
        String sessionId = sessionClient.createSession(session).getId();

        try {
            var name = session.getName().orElseThrow();
            assertThat(keyValueClient.acquireLock(key, name, sessionId)).isTrue();
            List<SessionInfo> result = sessionClient.listSessions();

            assertThat(result.stream().anyMatch(sessionInfo -> sessionId.equals(sessionInfo.getId()))).isTrue();
        } finally {
            keyValueClient.releaseLock(key, sessionId);
            keyValueClient.deleteKey(key);
            sessionClient.destroySession(sessionId);
        }
    }
}
