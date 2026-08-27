package com.flash.fulfill.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类(HS256 对称签名)。
 * <ul>
 *     <li>生成: {@link #generateToken(Long)} —— subject 存放 userId</li>
 *     <li>解析: {@link #parseUserId(String)} —— 从 token 还原 userId</li>
 *     <li>校验: {@link #validate(String)} —— 是否有效且未过期</li>
 * </ul>
 * <p>
 * 注意:非 Spring Bean(flash-common 的包不在各服务默认组件扫描范围内),
 * 由使用方 {@code new JwtUtils(secret, expireSeconds)} 或自行注册为 {@code @Bean}。
 * <p>
 * TODO 生产增强:密钥从配置中心下发 + 支持 RS256 非对称 + jti/黑名单实现登出与踢下线。
 */
public class JwtUtils {

    public static final long DEFAULT_EXPIRE_SECONDS = 86400L;

    private static final String BEARER_PREFIX = "Bearer ";

    /** HS256 要求密钥至少 256 位 = 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expireSeconds;

    public JwtUtils(String secret) {
        this(secret, DEFAULT_EXPIRE_SECONDS);
    }

    public JwtUtils(String secret, long expireSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret 长度必须 >= 32 字节(HS256 要求 256 位)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /** 生成 token,subject 携带 userId */
    public String generateToken(Long userId) {
        return generateToken(userId, expireSeconds);
    }

    /** 生成 token,可指定过期秒数 */
    public String generateToken(Long userId, long expireSeconds) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000L))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 token 中的 userId。
     *
     * @throws IllegalArgumentException token 为空或非法格式
     * @throws io.jsonwebtoken.JwtException token 被篡改或已过期
     */
    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(stripBearer(token)).getSubject());
    }

    /** 校验 token 是否有效且未过期(不抛异常) */
    public boolean validate(String token) {
        try {
            parseClaims(stripBearer(token));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 去掉可选的 "Bearer " 前缀 */
    private String stripBearer(String token) {
        if (token == null) {
            return null;
        }
        return token.startsWith(BEARER_PREFIX) ? token.substring(BEARER_PREFIX.length()) : token;
    }
}