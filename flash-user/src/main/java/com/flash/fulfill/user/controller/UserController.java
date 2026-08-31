package com.flash.fulfill.user.controller;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.common.constant.HttpHeaderNames;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.common.security.JwtUtils;
import com.flash.fulfill.user.dto.LoginCommand;
import com.flash.fulfill.user.dto.LoginResponse;
import com.flash.fulfill.user.dto.RegisterCommand;
import com.flash.fulfill.user.dto.UserView;
import com.flash.fulfill.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 * register / login 为公开接口(网关白名单),me 需认证。
 */
@RestController
public class UserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    public UserController(UserService userService, JwtUtils jwtUtils) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
    }

    /** 注册 */
    @PostMapping(ApiPaths.USER_REGISTER)
    public Result<UserView> register(@Valid @RequestBody RegisterCommand cmd) {
        return Result.ok(userService.register(cmd));
    }

    /** 登录,签发 JWT */
    @PostMapping(ApiPaths.USER_LOGIN)
    public Result<LoginResponse> login(@Valid @RequestBody LoginCommand cmd) {
        return Result.ok(userService.login(cmd));
    }

    /** 当前用户:优先解析 Authorization token;无有效 token 时才回退网关透传的 X-User-Id(直连兜底) */
    @GetMapping(ApiPaths.USER_ME)
    public Result<UserView> me(
            @RequestHeader(value = HttpHeaderNames.X_USER_ID, required = false) String xUserId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Long userId = resolveUserId(xUserId, authorization);
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未认证,请先登录");
        }
        return Result.ok(userService.me(userId));
    }

    private Long resolveUserId(String xUserId, String authorization) {
        if (authorization != null && authorization.startsWith(HttpHeaderNames.BEARER_PREFIX)) {
            try {
                return jwtUtils.parseUserId(authorization.substring(HttpHeaderNames.BEARER_PREFIX.length()));
            } catch (Exception ignored) {
                // token 非法,继续尝试 X-User-Id 兜底
            }
        }
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                return Long.valueOf(xUserId);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}