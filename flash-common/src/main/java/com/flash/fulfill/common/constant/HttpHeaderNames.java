package com.flash.fulfill.common.constant;

/**
 * 跨服务 HTTP 请求头与鉴权头常量。
 * <p>
 * 网关(写透传头)、用户服务(读 X-User-Id / Authorization)、Sentinel 限流规则(按 X-User-Id 取参)共用。
 * <p>
 * 命名取 HttpHeaderNames 以避免与 org.springframework.http.HttpHeaders 冲突。
 */
public final class HttpHeaderNames {

    /** 认证头 */
    public static final String AUTHORIZATION = "Authorization";

    /** 网关透传的登录用户 ID */
    public static final String X_USER_ID = "X-User-Id";

    /** ClientIdentityFilter 解析出的可信客户端 IP(网关写入) */
    public static final String X_CLIENT_IP = "X-Flash-Client-IP";

    /** 代理转发头:原始请求 IP */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** 代理转发头:真实 IP */
    public static final String X_REAL_IP = "X-Real-IP";

    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    private HttpHeaderNames() {
    }
}
