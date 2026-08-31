package com.flash.fulfill.user.config;

import com.flash.fulfill.common.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置:把 flash-common 的 JwtUtils 注册为 Bean,供登录签发 / me 解析使用。
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtUtils jwtUtils(
            @Value("${jwt.secret:flash-fulfill-demo-secret-0123456789abcdef}") String secret,
            @Value("${jwt.expire-seconds:" + JwtUtils.DEFAULT_EXPIRE_SECONDS + "}") long expireSeconds) {
        return new JwtUtils(secret, expireSeconds);
    }
}