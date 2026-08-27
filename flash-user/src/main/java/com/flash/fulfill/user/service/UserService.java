package com.flash.fulfill.user.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.common.security.JwtUtils;
import com.flash.fulfill.user.dto.LoginCommand;
import com.flash.fulfill.user.dto.LoginResponse;
import com.flash.fulfill.user.dto.RegisterCommand;
import com.flash.fulfill.user.dto.UserView;
import com.flash.fulfill.user.entity.User;
import com.flash.fulfill.user.mapper.UserMapper;
import com.flash.fulfill.user.session.UserSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务:注册 / 登录(签发 JWT) / 当前用户。
 */
@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final UserSessionStore sessionStore;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, JwtUtils jwtUtils, UserSessionStore sessionStore) {
        this.userMapper = userMapper;
        this.jwtUtils = jwtUtils;
        this.sessionStore = sessionStore;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /** 注册:用户名唯一,密码 BCrypt 加密存储(字段规则由 controller 层 @Valid 校验) */
    @Transactional
    public UserView register(RegisterCommand cmd) {
        if (userMapper.selectByUsername(cmd.getUsername()) != null) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(cmd.getUsername());
        user.setPasswordHash(passwordEncoder.encode(cmd.getPassword()));
        user.setNickname(cmd.getNickname());
        userMapper.insert(user);
        log.info("新用户注册成功 userId={} username={}", user.getId(), user.getUsername());
        return toView(user);
    }

    /** 登录:校验密码,签发 JWT,并将会话写入 Redis(不区分"用户不存在/密码错误",避免暴露用户是否注册) */
    public LoginResponse login(LoginCommand cmd) {
        User user = userMapper.selectByUsername(cmd.getUsername());
        if (user == null || !passwordEncoder.matches(cmd.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserView view = toView(user);
        String token = jwtUtils.generateToken(user.getId());
        sessionStore.createSession(token, view);
        log.info("用户登录成功 userId={}", user.getId());
        return new LoginResponse(token, view);
    }

    /** 当前用户:根据已认证的 userId 查询 */
    public UserView me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return toView(user);
    }

    private UserView toView(User user) {
        UserView v = new UserView();
        v.setId(user.getId());
        v.setUsername(user.getUsername());
        v.setNickname(user.getNickname());
        return v;
    }
}