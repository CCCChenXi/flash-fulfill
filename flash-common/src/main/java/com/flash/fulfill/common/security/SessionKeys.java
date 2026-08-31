package com.flash.fulfill.common.security;

import com.flash.fulfill.common.constant.RedisKeys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 会话 Redis Key 工具。
 * <p>
 * 键 = {@code user:session:{SHA-256(token)}},避免以明文 token 作为长 key;
 * flash-user(写入)与 flash-gateway(校验)共用同一套 key 规则。
 */
public final class SessionKeys {

    private SessionKeys() {
    }

    /** 由 token 计算会话 key */
    public static String of(String token) {
        return RedisKeys.USER_SESSION_PREFIX + sha256Hex(token);
    }

    private static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}