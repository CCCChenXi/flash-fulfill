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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final String SECRET = "flash-fulfill-demo-secret-0123456789abcdef";
    private static final String USERNAME = "alice";
    private static final String PASSWORD = "123456";

    private UserMapper userMapper;
    private UserSessionStore sessionStore;
    private UserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        sessionStore = mock(UserSessionStore.class);
        service = new UserService(userMapper, new JwtUtils(SECRET), sessionStore);
    }

    private RegisterCommand buildRegisterCommand() {
        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername(USERNAME);
        cmd.setPassword(PASSWORD);
        cmd.setNickname("爱丽丝");
        return cmd;
    }

    @Test
    void registerSuccessEncodesPasswordAndReturnsView() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(null);

        UserView view = service.register(buildRegisterCommand());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals(USERNAME, saved.getUsername());
        assertNotNull(saved.getPasswordHash());
        // 存的是 BCrypt 哈希,且能匹配明文
        org.junit.jupiter.api.Assertions.assertTrue(
                new BCryptPasswordEncoder().matches(PASSWORD, saved.getPasswordHash()));
        assertEquals(USERNAME, view.getUsername());
    }

    @Test
    void registerDuplicateUsernameThrows() {
        User existing = new User();
        existing.setUsername(USERNAME);
        when(userMapper.selectByUsername(USERNAME)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class,
                () -> service.register(buildRegisterCommand()));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void loginSuccessReturnsTokenWithUserId() {
        User user = new User();
        user.setId(1001L);
        user.setUsername(USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(PASSWORD));
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user);

        LoginCommand cmd = new LoginCommand();
        cmd.setUsername(USERNAME);
        cmd.setPassword(PASSWORD);
        LoginResponse resp = service.login(cmd);

        assertNotNull(resp.getToken());
        // token 里能解析回同一个 userId
        assertEquals(1001L, new JwtUtils(SECRET).parseUserId(resp.getToken()));
        assertEquals(USERNAME, resp.getUser().getUsername());
    }

    @Test
    void loginSuccessStoresSessionInRedis() {
        User user = new User();
        user.setId(1001L);
        user.setUsername(USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(PASSWORD));
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user);

        LoginCommand cmd = new LoginCommand();
        cmd.setUsername(USERNAME);
        cmd.setPassword(PASSWORD);
        LoginResponse resp = service.login(cmd);

        // 会话写入 Redis,且带登录产生的 token 与用户信息
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UserView> viewCaptor = ArgumentCaptor.forClass(UserView.class);
        verify(sessionStore).createSession(tokenCaptor.capture(), viewCaptor.capture());
        assertEquals(resp.getToken(), tokenCaptor.getValue());
        assertEquals(1001L, viewCaptor.getValue().getId());
        assertEquals(USERNAME, viewCaptor.getValue().getUsername());
    }

    @Test
    void loginWrongPasswordThrows() {
        User user = new User();
        user.setId(1001L);
        user.setUsername(USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("654321"));
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user);

        LoginCommand cmd = new LoginCommand();
        cmd.setUsername(USERNAME);
        cmd.setPassword(PASSWORD);
        BizException ex = assertThrows(BizException.class, () -> service.login(cmd));

        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
    }

    @Test
    void loginUnknownUserThrowsSameCredentialError() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(null);

        LoginCommand cmd = new LoginCommand();
        cmd.setUsername(USERNAME);
        cmd.setPassword(PASSWORD);
        BizException ex = assertThrows(BizException.class, () -> service.login(cmd));

        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
    }

    @Test
    void meReturnsUserView() {
        User user = new User();
        user.setId(1001L);
        user.setUsername(USERNAME);
        user.setNickname("爱丽丝");
        when(userMapper.selectById(1001L)).thenReturn(user);

        UserView view = service.me(1001L);

        assertEquals(1001L, view.getId());
        assertEquals(USERNAME, view.getUsername());
        assertEquals("爱丽丝", view.getNickname());
    }

    @Test
    void meUserNotFoundThrows() {
        when(userMapper.selectById(9999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.me(9999L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }
}