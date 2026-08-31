package com.flash.fulfill.order.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.order.service.OrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单内部状态回调接口(仅供内网服务调用,不对外暴露)。
 */
@RestController
public class OrderInternalController {

    private final OrderService orderService;

    public OrderInternalController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 履约完成后回调订单置为已发货。
     */
    @PutMapping(ApiPaths.ORDER_INTERNAL_DISPATCH)
    public Result<Boolean> markDispatched(@PathVariable("orderNo") String orderNo) {
        orderService.markDispatched(orderNo);
        return Result.ok(true);
    }

    /**
     * 支付成功回调(预留:未来支付服务接入),订单进入待发货并触发履约。
     */
    @PutMapping(ApiPaths.ORDER_INTERNAL_PAID)
    public Result<Boolean> markPaid(@PathVariable("orderNo") String orderNo) {
        orderService.markPaid(orderNo);
        return Result.ok(true);
    }

    /**
     * 确认收货/配送完成回调。
     */
    @PutMapping(ApiPaths.ORDER_INTERNAL_COMPLETE)
    public Result<Boolean> markCompleted(@PathVariable("orderNo") String orderNo) {
        orderService.markCompleted(orderNo);
        return Result.ok(true);
    }

    /**
     * 取消订单。
     */
    @PutMapping(ApiPaths.ORDER_INTERNAL_CANCEL)
    public Result<Boolean> cancel(@PathVariable("orderNo") String orderNo) {
        orderService.cancel(orderNo);
        return Result.ok(true);
    }
}