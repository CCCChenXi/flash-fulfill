package com.flash.fulfill.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.order.entity.FlashOrder;
import org.apache.ibatis.annotations.Param;

/**
 * 订单 Mapper:新增/更新走 BaseMapper(insert / updateById),按订单号/幂等键查询走 XML 自定义 SQL。
 */
public interface OrderMapper extends BaseMapper<FlashOrder> {

    FlashOrder selectByOrderNo(@Param("orderNo") String orderNo);

    FlashOrder selectByRequestId(@Param("requestId") String requestId);

    /** 幂等判断:同一 requestId 是否已处理过 */
    boolean existsByRequestId(@Param("requestId") String requestId);
}
