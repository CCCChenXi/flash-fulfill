package com.flash.fulfill.order.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.dto.FlashOrderView;
import com.flash.fulfill.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单查询接口。
 */
@RestController
@RequestMapping("/api/order")
public class OrderQueryController {

    private final OrderService orderService;

    public OrderQueryController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 客户端凭秒抢返回的 requestId 轮询订单状态。
     */
    @GetMapping("/flash-orders")
    public Result<FlashOrderView> queryByRequestId(@RequestParam("requestId") String requestId) {
        return Result.ok(orderService.queryByRequestId(requestId));
    }

    @GetMapping("/orders/{orderNo}")
    public Result<FlashOrderView> queryByOrderNo(@PathVariable("orderNo") String orderNo) {
        return Result.ok(orderService.queryByOrderNo(orderNo));
    }
}