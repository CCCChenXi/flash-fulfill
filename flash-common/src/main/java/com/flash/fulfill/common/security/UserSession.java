package com.flash.fulfill.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户会话信息(登录成功后写入 Redis 的值)。
 * <p>
 * 键为 {@code user:session:{tokenHash}},值为本对象序列化后的 JSON。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    private Long userId;

    private String username;

    private String nickname;

    private Long loginAt;
}