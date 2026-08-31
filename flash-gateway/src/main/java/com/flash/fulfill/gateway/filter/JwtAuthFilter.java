package com.flash.fulfill.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.common.constant.HttpHeaderNames;
import com.flash.fulfill.common.security.JwtUtils;
import com.flash.fulfill.common.security.SessionKeys;
import com.flash.fulfill.common.security.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关鉴权过滤器。
 * <p>
 * 认证方式:{@code Authorization: Bearer <jwt>},校验签名并解析 userId 后,
 * 再查 Redis 会话 {@code user:session:{tokenHash}} 比对 userId,一致才放行并透传 X-User-Id。
 * <p>
 * 业务参数校验(如 requestId 非空)由各服务 controller 层 @Valid 负责,网关仅做鉴权与身份透传。
 * <p>
 * TODO 生产:密钥走配置中心/环境变量,支持多算法(RS256),登出时删除 Redis 会话即可实现踢下线。
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -100;

    /** 无需认证的公开接口(注册/登录) */
    private static final List<String> PUBLIC_PATHS = List.of(
            ApiPaths.USER_LOGIN,
            ApiPaths.USER_REGISTER);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtUtils jwtUtils;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(
            @Value("${jwt.secret:flash-fulfill-demo-secret-0123456789abcdef}") String secret,
            @Value("${jwt.expire-seconds:" + JwtUtils.DEFAULT_EXPIRE_SECONDS + "}") long expireSeconds,
            ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtils = new JwtUtils(secret, expireSeconds);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(HttpHeaderNames.BEARER_PREFIX)) {
            return unauthorized(exchange);
        }
        String token = authorization.substring(HttpHeaderNames.BEARER_PREFIX.length());

        final Long userId;
        try {
            userId = jwtUtils.parseUserId(token);
        } catch (Exception e) {
            log.warn("JWT 校验失败,拒绝访问 err={}", e.getMessage());
            return unauthorized(exchange);
        }

        // 校验 Redis 会话:token 哈希对应的 session 存在且 userId 一致
        return redisTemplate.opsForValue().get(SessionKeys.of(token))
                .defaultIfEmpty("")
                .flatMap(json -> {
                    if (json.isEmpty()) {
                        log.warn("会话不存在,拒绝访问 userId={}", userId);
                        return unauthorized(exchange);
                    }
                    try {
                        UserSession session = objectMapper.readValue(json, UserSession.class);
                        if (userId.equals(session.getUserId())) {
                            return chain.filter(withUserId(exchange, String.valueOf(userId)));
                        }
                        log.warn("会话 userId 不一致,拒绝访问 userId={}", userId);
                    } catch (Exception e) {
                        log.warn("会话 JSON 解析失败 err={}", e.getMessage());
                    }
                    return unauthorized(exchange);
                })
                .onErrorResume(e -> {
                    log.warn("会话校验异常,拒绝访问 err={}", e.getMessage());
                    return unauthorized(exchange);
                })
                .then();
    }

    private ServerWebExchange withUserId(ServerWebExchange exchange, String userId) {
        ServerHttpRequest request = exchange.getRequest().mutate().header(HttpHeaderNames.X_USER_ID, userId).build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Result.fail(ErrorCode.GATEWAY_AUTH_FAILED));
        } catch (Exception e) {
            body = ("{\"code\":" + ErrorCode.UNAUTHORIZED.getCode() + ",\"message\":\"unauthorized\"}").getBytes();
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}