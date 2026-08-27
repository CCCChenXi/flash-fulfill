package com.flash.fulfill.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIdentityFilterTest {

    private static final String IP = "203.0.113.5";

    private MockServerWebExchange exchangeWith(String remoteIp, String xff, String xRealIp) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/seckill/flash-orders");
        if (remoteIp != null) {
            builder.remoteAddress(new InetSocketAddress(remoteIp, 8080));
        }
        if (xff != null) {
            builder.header("X-Forwarded-For", xff);
        }
        if (xRealIp != null) {
            builder.header("X-Real-IP", xRealIp);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void untrustedProxyIgnoresForwardedHeadersAndUsesRemoteAddress() {
        ClientIdentityFilter filter = new ClientIdentityFilter(List.of());
        ServerWebExchange exchange = exchangeWith(IP, "1.2.3.4", "5.6.7.8");

        assertEquals(IP, filter.resolveClientIp(exchange));
    }

    @Test
    void trustedProxyUsesFirstForwardedForEntry() {
        ClientIdentityFilter filter = new ClientIdentityFilter(List.of("10.0.0.1"));
        ServerWebExchange exchange = exchangeWith("10.0.0.1", "1.2.3.4, 9.9.9.9", null);

        assertEquals("1.2.3.4", filter.resolveClientIp(exchange));
    }

    @Test
    void trustedProxyFallsBackToRealIpWhenNoForwardedFor() {
        ClientIdentityFilter filter = new ClientIdentityFilter(List.of("10.0.0.1"));
        ServerWebExchange exchange = exchangeWith("10.0.0.1", null, "5.6.7.8");

        assertEquals("5.6.7.8", filter.resolveClientIp(exchange));
    }

    @Test
    void trustedProxyWithoutForwardHeadersUsesRemoteAddress() {
        ClientIdentityFilter filter = new ClientIdentityFilter(List.of("10.0.0.1"));
        ServerWebExchange exchange = exchangeWith("10.0.0.1", null, null);

        assertEquals("10.0.0.1", filter.resolveClientIp(exchange));
    }

    @Test
    void noRemoteAddressReturnsUnknown() {
        ClientIdentityFilter filter = new ClientIdentityFilter(List.of());
        ServerWebExchange exchange = exchangeWith(null, "1.2.3.4", null);

        assertEquals("unknown", filter.resolveClientIp(exchange));
    }

    @Test
    void filterPropagatesClientIpHeaderToDownstream() {
        ClientIdentityFilter filter = new ClientIdentityFilter(List.of());
        MockServerWebExchange exchange = exchangeWith(IP, null, null);
        AtomicReference<String> seen = new AtomicReference<>();
        GatewayFilterChain chain = e -> {
            seen.set(e.getRequest().getHeaders().getFirst(ClientIdentityFilter.CLIENT_IP_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertEquals(IP, seen.get());
    }
}