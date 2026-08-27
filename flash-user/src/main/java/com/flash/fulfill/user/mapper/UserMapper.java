package com.flash.fulfill.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.user.entity.User;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper:新增/按 ID 查询走 BaseMapper,按用户名查询走 XML 自定义 SQL。
 */
public interface UserMapper extends BaseMapper<User> {

    User selectByUsername(@Param("username") String username);
}