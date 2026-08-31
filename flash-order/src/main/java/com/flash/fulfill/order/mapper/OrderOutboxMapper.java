package com.flash.fulfill.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.order.entity.OrderOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单 Outbox Mapper:标准 CRUD 走 BaseMapper;轮询转发(PENDING 抢取 / 置 SENT / 重试计数)走 XML 自定义 SQL。
 */
public interface OrderOutboxMapper extends BaseMapper<OrderOutbox> {

    List<OrderOutbox> selectPending(@Param("limit") int limit);

    int markSent(@Param("id") Long id);

    int incrementRetry(@Param("id") Long id);
}