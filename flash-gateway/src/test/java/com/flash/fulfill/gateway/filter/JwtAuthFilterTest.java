package com.flash.fulfill.gateway.filter;

import com.flash.fulfill.common.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "flash-fulfill-demo-secret-0123456789abcdef";
    private static final long USER_ID = 1001L;

    private JwtAuthFilter filter;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        filter = new JwtAuthFilter(SECRET, 86400, redisTemplate);
    }

    private String sessionJson(Long userId) {
        return "{\"userId\":" + userId + ",\"username\":\"alice\",\"nickname\":\"爱丽丝\",\"loginAt\":1700000000000}";
    }

    @Test
    void rejectsMissingToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/seckill/flash-orders").build());
        AtomicBoolean invoked = new AtomicBoolean(false);
        GatewayFilterChain chain = e -> {
            invoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(invoked.get());
    }

    @Test
    void rejectsWrongToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/seckill/flash-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                        .build());

        filter.filter(exchange, chainStub()).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }


    @Test
    void allowsJwtTokenWhenRedisSessionMatches() {
        String token = new JwtUtils(SECRET).generateToken(USER_ID);
        when(valueOps.get(anyString())).thenReturn(Mono.just(sessionJson(USER_ID)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/seckill/flash-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        AtomicBoolean invoked = new AtomicBoolean(false);
        AtomicBoolean userIdSeen = new AtomicBoolean(false);
        GatewayFilterChain chain = e -> {
            invoked.set(true);
            if (String.valueOf(USER_ID).equals(e.getRequest().getHeaders().getFirst("X-User-Id"))) {
                userIdSeen.set(true);
            }
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertTrue(invoked.get());
        assertTrue(userIdSeen.get());
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsJwtWhenRedisSessionMissing() {
        String token = new JwtUtils(SECRET).generateToken(USER_ID);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/seckill/flash-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, chainStub()).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsJwtWhenRedisSessionUserIdMismatch() {
        String token = new JwtUtils(SECRET).generateToken(USER_ID);
        when(valueOps.get(anyString())).thenReturn(Mono.just(sessionJson(9999L)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/seckill/flash-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, chainStub()).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsTamperedJwtToken() {
        JwtUtils jwt = new JwtUtils(SECRET);
        String token = jwt.generateToken(USER_ID);
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("a") ? "b" : "a");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/seckill/flash-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                        .build());

        filter.filter(exchange, chainStub()).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void allowsPublicPathWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/login").build());
        AtomicBoolean invoked = new AtomicBoolean(false);
        GatewayFilterChain chain = e -> {
            invoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertTrue(invoked.get());
    }

    private GatewayFilterChain chainStub() {
        return e -> Mono.empty();
    }
}