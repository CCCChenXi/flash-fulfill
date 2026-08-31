package com.flash.fulfill.order.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.order.dto.OrderConfirmCommand;
import com.flash.fulfill.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单确认接口(客户端在订单详情页填收货地址后提交)。
 */
@RestController
public class OrderConfirmController {

    private final OrderService orderService;

    public OrderConfirmController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 确认订单:写入收货地址,订单 INITIAL -> PENDING_PAYMENT。
     */
    @PutMapping(ApiPaths.ORDER_CONFIRM)
    public Result<Boolean> confirm(@PathVariable("orderNo") String orderNo,
                                   @Valid @RequestBody OrderConfirmCommand cmd) {
        orderService.confirmOrderInfo(orderNo, cmd);
        return Result.ok(true);
    }

    /**
     * 确认收货:订单 SHIPPED -> COMPLETED。
     */
    @PutMapping(ApiPaths.ORDER_CONFIRM_RECEIPT)
    public Result<Boolean> confirmReceipt(@PathVariable("orderNo") String orderNo) {
        orderService.markCompleted(orderNo);
        return Result.ok(true);
    }
}