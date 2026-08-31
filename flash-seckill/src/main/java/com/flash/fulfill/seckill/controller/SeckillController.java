package com.flash.fulfill.seckill.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.common.dto.FlashOrderResponse;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.seckill.constant.SeckillConstant;
import com.flash.fulfill.seckill.service.SeckillService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒抢接口。
 */
@RestController
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /**
     * 提交秒抢下单请求。
     * 同步完成预扣与消息投递,订单异步创建,客户端可凭返回的 requestId 轮询订单状态。
     */
    @PostMapping(ApiPaths.SECKILL_FLASH_ORDERS)
    @SentinelResource(SeckillConstant.SENTINEL_RESOURCE_CREATE)
    public Result<FlashOrderResponse> createFlashOrder(@Valid @RequestBody SeckillOrderCommand command) {
        return seckillService.createFlashOrder(command);
    }
}