package com.flash.fulfill.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.security.JwtUtils;
import com.flash.fulfill.user.config.JwtConfig;
import com.flash.fulfill.user.dto.RegisterCommand;
import com.flash.fulfill.user.dto.UserView;
import com.flash.fulfill.user.mapper.UserMapper;
import com.flash.fulfill.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 层 @Valid 校验链路测试:
 * 非法 body → MethodArgumentNotValidException → GlobalExceptionHandler → Result(code=10001)。
 */
@WebMvcTest(UserController.class)
@Import(JwtConfig.class)
class UserControllerTest {

    private static final String JWT_SECRET = "flash-fulfill-demo-secret-0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    /** 替换 @MapperScan 注册的真实 MapperFactoryBean,避免 WebMvcTest 缺少 SqlSessionFactory */
    @MockBean
    private UserMapper userMapper;

    @Test
    void registerRejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value(containsString("请求体缺失或格式错误")));

        verify(userService, never()).register(any());
    }

    @Test
    void registerRejectsBlankUsername() throws Exception {
        // 空格满足长度但违反 @NotBlank,确保唯一触发该约束(避免 @Size 同报造成顺序不确定)
        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("   ");
        cmd.setPassword("123456");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value(containsString("用户名不能为空")));

        verify(userService, never()).register(any());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("alice");
        cmd.setPassword("123");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value(containsString("密码长度需在 6~32 位之间")));

        verify(userService, never()).register(any());
    }

    @Test
    void registerValidBodyCallsService() throws Exception {
        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("alice");
        cmd.setPassword("123456");
        UserView view = new UserView();
        view.setId(1001L);
        view.setUsername("alice");
        when(userService.register(any())).thenReturn(view);

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("alice"));

        verify(userService).register(any());
    }

    @Test
    void loginRejectsBlankPassword() throws Exception {
        String body = "{\"username\":\"alice\",\"password\":\"\"}";

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value(containsString("密码不能为空")));

        verify(userService, never()).login(any());
    }

    @Test
    void mePrefersTokenOverSpoofedUserIdHeader() throws Exception {
        String token = new JwtUtils(JWT_SECRET).generateToken(1001L);

        mockMvc.perform(get("/api/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "9999"))
                .andExpect(status().isOk());

        // token 优先,伪造的 X-User-Id 不生效
        verify(userService).me(1001L);
    }

    @Test
    void meFallsBackToUserIdHeaderWhenNoToken() throws Exception {
        mockMvc.perform(get("/api/user/me")
                        .header("X-User-Id", "1001"))
                .andExpect(status().isOk());

        verify(userService).me(1001L);
    }

    @Test
    void meRejectsWhenNoAuth() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}