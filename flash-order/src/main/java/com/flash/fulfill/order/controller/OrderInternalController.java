package com.flash.fulfill.order.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.order.service.OrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单内部状态回调接口(仅供内网服务调用,不对外暴露)。
 */
@RestController
@RequestMapping("/api/order/internal")
public class OrderInternalController {

    private final OrderService orderService;

    public OrderInternalController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 履约完成后回调订单置为已发货。
     */
    @PutMapping("/orders/{orderNo}/dispatched")
    public Result<Boolean> markDispatched(@PathVariable("orderNo") String orderNo) {
        orderService.markDispatched(orderNo);
        return Result.ok(true);
    }
}