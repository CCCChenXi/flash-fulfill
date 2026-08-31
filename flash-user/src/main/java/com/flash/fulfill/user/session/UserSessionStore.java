package com.flash.fulfill.user.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.security.JwtUtils;
import com.flash.fulfill.common.security.SessionKeys;
import com.flash.fulfill.common.security.UserSession;
import com.flash.fulfill.user.dto.UserView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 用户会话存储:登录成功后将 session 写入 Redis。
 * <p>
 * 键 = {@code user:session:{tokenHash}},值 = 用户信息 JSON,TTL 与 JWT 过期时间一致。
 * 网关侧据此做每次请求的会话校验(白名单式,支持多设备同时在线)。
 */
@Slf4j
@Component
public class UserSessionStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long expireSeconds;

    public UserSessionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${jwt.expire-seconds:" + JwtUtils.DEFAULT_EXPIRE_SECONDS + "}") long expireSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.expireSeconds = expireSeconds;
    }

    /** 登录成功后写入会话 */
    public void createSession(String token, UserView user) {
        UserSession session = new UserSession(
                user.getId(), user.getUsername(), user.getNickname(), System.currentTimeMillis());
        try {
            redisTemplate.opsForValue().set(
                    SessionKeys.of(token), objectMapper.writeValueAsString(session), Duration.ofSeconds(expireSeconds));
            log.info("会话已写入 Redis userId={}", user.getId());
        } catch (Exception e) {
            throw new IllegalStateException("会话写入 Redis 失败", e);
        }
    }

    /** 读取会话 */
    public Optional<UserSession> getSession(String token) {
        String json = redisTemplate.opsForValue().get(SessionKeys.of(token));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(json, UserSession.class));
        } catch (Exception e) {
            log.warn("会话 JSON 解析失败,视为不存在 err={}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 删除会话(登出/踢下线用) */
    public void removeSession(String token) {
        redisTemplate.delete(SessionKeys.of(token));
    }
}