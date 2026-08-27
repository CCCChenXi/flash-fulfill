package com.flash.fulfill.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 客户端身份过滤器:解析可信客户端 IP 并写入 {@code X-Flash-Client-IP} 头,供 Sentinel 按 IP 限流。
 * <p>
 * 防伪造:仅当直连对端命中 {@code gateway.rate-limit.trusted-proxies} 时才信任
 * {@code X-Forwarded-For}/{@code X-Real-IP},否则一律使用 {@code getRemoteAddress()}(默认安全)。
 */
@Slf4j
@Component
public class ClientIdentityFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -200;

    public static final String CLIENT_IP_HEADER = "X-Flash-Client-IP";

    private final List<String> trustedProxies;

    public ClientIdentityFilter(@Value("${gateway.rate-limit.trusted-proxies:}") List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange);
        ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate().header(CLIENT_IP_HEADER, clientIp).build())
                .build();
        return chain.filter(mutated);
    }

    String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String remoteIp = remote != null ? remote.getAddress().getHostAddress() : null;
        if (remoteIp != null && trustedProxies.contains(remoteIp)) {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp;
            }
        }
        return remoteIp != null ? remoteIp : "unknown";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}