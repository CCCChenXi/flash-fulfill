package com.flash.fulfill.seckill.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.SeckillResultCode;
import com.flash.fulfill.common.dto.FlashOrderResponse;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.seckill.script.SeckillScriptExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 秒抢下单服务。
 * <p>
 * 流程:参数校验 → Lua 原子幂等(状态机占位 + 限购 + 库存扣减 + 写建单事件到 Redis Stream) → 按状态码分派。
 * 扣减成功即"已受理"并返回,事件由独立 flash-relay 服务消费转发 RocketMQ,请求线程不再同步发送 MQ。
 * <p>
 * 该设计消除了"扣减库存"与"发送消息"之间的不一致窗口:扣减与写事件同一次 EVAL 原子完成,
 * 消息投递由 relay 异步可靠重试,不存在"MQ 已发但客户端超时误回滚"的时序问题。
 * <p>
 * TODO 生产增强:对账回滚超时未支付的预扣额度。
 */
@Slf4j
@Service
public class SeckillService {

    private final SeckillScriptExecutor scriptExecutor;

    @Value("${seckill.buy.limit:1}")
    private int buyLimit;

    @Value("${seckill.req.ttl.secs:600}")
    private int reqTtlSeconds;

    public SeckillService(SeckillScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    public Result<FlashOrderResponse> createFlashOrder(SeckillOrderCommand cmd) {
        int resultCode = scriptExecutor.execute(cmd, buyLimit, reqTtlSeconds);

        switch (resultCode) {
            case SeckillResultCode.SUCCESS:
                return accepted(cmd);
            case SeckillResultCode.OFF_SHELF:
                return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "商品已下架", SeckillResultCode.OFF_SHELF));
            case SeckillResultCode.STOCK_NOT_ENOUGH:
                return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "库存不足", SeckillResultCode.STOCK_NOT_ENOUGH));
            case SeckillResultCode.NOT_EXIST:
                return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "商品不存在", SeckillResultCode.NOT_EXIST));
            case SeckillResultCode.PROCESSING:
                return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "下单处理中,请勿重复提交", SeckillResultCode.PROCESSING));
            case SeckillResultCode.LIMIT:
                return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "已达限购数量", SeckillResultCode.LIMIT));
            case SeckillResultCode.PRICE_UNAVAILABLE:
                return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "商品价格未就绪,请稍后重试", SeckillResultCode.PRICE_UNAVAILABLE));
            case SeckillResultCode.INVALID_PARAM:
                throw new BizException(ErrorCode.INVALID_PARAM, "购买数量非法");
            default:
                throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 扣减成功即受理:建单事件已写入 Stream,由 relay 异步转发,无需请求线程等待 MQ。 */
    private Result<FlashOrderResponse> accepted(SeckillOrderCommand cmd) {
        log.info("秒杀下单已受理 requestId={} skuId={} userId={}", cmd.getRequestId(), cmd.getSkuId(), cmd.getUserId());
        return Result.ok(new FlashOrderResponse(cmd.getRequestId(), "下单请求已受理,正在异步创建订单", SeckillResultCode.SUCCESS));
    }
}