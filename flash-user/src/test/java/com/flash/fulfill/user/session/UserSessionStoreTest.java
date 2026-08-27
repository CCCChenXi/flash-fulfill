package com.flash.fulfill.user.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.security.UserSession;
import com.flash.fulfill.user.dto.UserView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionStoreTest {

    private static final String TOKEN = "jwt-token-001";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private UserSessionStore store;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new UserSessionStore(redisTemplate, new ObjectMapper(), 86400);
    }

    private UserView view() {
        UserView v = new UserView();
        v.setId(1001L);
        v.setUsername("alice");
        v.setNickname("爱丽丝");
        return v;
    }

    @Test
    void createSessionWritesJsonWithTtl() throws Exception {
        store.createSession(TOKEN, view());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertTrue(keyCaptor.getValue().startsWith("user:session:"));
        assertEquals(Duration.ofSeconds(86400), ttlCaptor.getValue());

        UserSession parsed = new ObjectMapper().readValue(valueCaptor.getValue(), UserSession.class);
        assertEquals(1001L, parsed.getUserId());
        assertEquals("alice", parsed.getUsername());
        assertEquals("爱丽丝", parsed.getNickname());
        assertTrue(parsed.getLoginAt() > 0);
    }

    @Test
    void getSessionParsesStoredJson() throws Exception {
        String json = "{\"userId\":1001,\"username\":\"alice\",\"nickname\":\"爱丽丝\",\"loginAt\":1700000000000}";
        when(valueOps.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(json);

        Optional<UserSession> session = store.getSession(TOKEN);

        assertTrue(session.isPresent());
        assertEquals(1001L, session.get().getUserId());
        assertEquals("alice", session.get().getUsername());
    }

    @Test
    void getSessionReturnsEmptyWhenMissing() {
        when(valueOps.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

        assertTrue(store.getSession(TOKEN).isEmpty());
    }
}