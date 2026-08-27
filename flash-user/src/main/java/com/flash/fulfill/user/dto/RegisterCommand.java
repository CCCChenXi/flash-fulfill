package com.flash.fulfill.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求。
 */
@Data
@NoArgsConstructor
public class RegisterCommand {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 10, message = "用户名长度需在 2~10 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;

    @Size(max = 50, message = "昵称最长 50 字符")
    private String nickname;
}