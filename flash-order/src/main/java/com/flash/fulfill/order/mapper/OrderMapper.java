package com.flash.fulfill.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.order.entity.FlashOrder;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper:新增/更新走 BaseMapper(insert / updateById),按订单号/幂等键查询走 XML 自定义 SQL。
 * 建单幂等由 flash_order.uk_request_id 唯一键保证,不再预查询去重。
 */
public interface OrderMapper extends BaseMapper<FlashOrder> {

    FlashOrder selectByOrderNo(@Param("orderNo") String orderNo);

    FlashOrder selectByRequestId(@Param("requestId") String requestId);

    /** 超时未支付订单(待支付/待确认),用于定时关单扫描 */
    List<FlashOrder> selectExpiredPending(@Param("expireTime") LocalDateTime expireTime,
                                          @Param("limit") int limit);

    /** 已发货超时未确认收货订单,用于超时自动完成扫描 */
    List<FlashOrder> selectExpiredShipped(@Param("expireTime") LocalDateTime expireTime,
                                          @Param("limit") int limit);
}
