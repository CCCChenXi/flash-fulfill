package com.flash.fulfill.seckill.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.dto.FlashOrderResponse;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.common.util.IdGenerator;
import com.flash.fulfill.seckill.deductor.StockPreDeductor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 秒抢下单服务。
 * <p>
 * 流程:参数校验 → Redis 预扣(可选) → 发送 MQ 命令(FLASH_ORDER_CREATE) → 立即返回受理结果。
 * <p>
 * TODO 生产增强:
 * 1. RocketMQ 事务消息:本地预扣 + 事务发送二阶段,避免"预扣成功但消息未达";
 * 2. 失败兜底:对账任务核对 redis 预扣与订单/库存,回滚超时未支付的预扣额度;
 * 3. 15 分钟超时关单由 order 侧延迟队列完成。
 */
@Slf4j
@Service
public class SeckillService {

    private final StockPreDeductor stockPreDeductor;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${seckill.prededuct.enabled:true}")
    private boolean predeductEnabled;

    public SeckillService(StockPreDeductor stockPreDeductor, RocketMQTemplate rocketMQTemplate) {
        this.stockPreDeductor = stockPreDeductor;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public Result<FlashOrderResponse> createFlashOrder(SeckillOrderCommand cmd) {
        validate(cmd);

        if (predeductEnabled && !stockPreDeductor.tryPreDeduct(cmd.getSkuId(), cmd.getQuantity())) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }

        if (cmd.getRequestId() == null || cmd.getRequestId().isBlank()) {
            cmd.setRequestId(IdGenerator.requestId());
        }

        String destination = MqTopics.FLASH_ORDER_CREATE + ":" + MqTopics.TAG_ORDER_CREATE;
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(destination, cmd, 3000);
            log.info("发送建单命令成功 requestId={} sendResult={}", cmd.getRequestId(), sendResult.getSendStatus());
        } catch (Exception e) {
            // 发送失败:回滚预扣,避免库存泄漏
            rollbackIfNeeded(cmd);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "下单请求排队失败,请稍后重试");
        }

        return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "下单请求已受理,正在异步创建订单"));
    }

    private void rollbackIfNeeded(SeckillOrderCommand cmd) {
        try {
            if (predeductEnabled) {
                stockPreDeductor.rollback(cmd.getSkuId(), cmd.getQuantity());
            }
        } catch (Exception e) {
            log.error("回滚预扣库存失败 skuId={} quantity={}", cmd.getSkuId(), cmd.getQuantity(), e);
        }
    }

    private void validate(SeckillOrderCommand cmd) {
        if (cmd == null || cmd.getUserId() == null || cmd.getSkuId() == null
                || cmd.getQuantity() == null || cmd.getQuantity() <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "userId/skuId/quantity 为必填且 quantity 需大于 0");
        }
    }
}