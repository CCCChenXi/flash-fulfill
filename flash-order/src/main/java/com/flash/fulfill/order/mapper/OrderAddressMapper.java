package com.flash.fulfill.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.order.entity.OrderAddress;
import org.apache.ibatis.annotations.Param;

/**
 * 订单收货地址 Mapper:标准 CRUD 走 BaseMapper,按订单查询走 XML 自定义 SQL。
 */
public interface OrderAddressMapper extends BaseMapper<OrderAddress> {

    OrderAddress selectByOrderId(@Param("orderId") Long orderId);
}