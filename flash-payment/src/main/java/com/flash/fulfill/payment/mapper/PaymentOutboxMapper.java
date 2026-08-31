package com.flash.fulfill.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.payment.entity.PaymentOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 支付 Outbox Mapper:selectPending 用 FOR UPDATE SKIP LOCKED 抢取,多实例轮询不重复处理。
 */
public interface PaymentOutboxMapper extends BaseMapper<PaymentOutbox> {

    List<PaymentOutbox> selectPending(@Param("limit") int limit);

    int markSent(@Param("id") Long id);

    int incrementRetry(@Param("id") Long id);
}