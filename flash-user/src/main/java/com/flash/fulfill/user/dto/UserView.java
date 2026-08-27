package com.flash.fulfill.user.dto;

import lombok.Data;

/**
 * 用户信息视图(不含敏感字段)。
 */
@Data
public class UserView {

    private Long id;

    private String username;

    private String nickname;
}