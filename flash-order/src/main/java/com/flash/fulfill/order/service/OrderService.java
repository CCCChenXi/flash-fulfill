package com.flash.fulfill.order.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.constant.OrderStatus;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.dto.DeductStockResult;
import com.flash.fulfill.common.dto.FlashOrderView;
import com.flash.fulfill.common.dto.OrderFulfillEvent;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.common.util.IdGenerator;
import com.flash.fulfill.order.entity.FlashOrder;
import com.flash.fulfill.order.feign.InventoryClient;
import com.flash.fulfill.order.feign.ProductClient;
import com.flash.fulfill.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 订单服务:处理秒抢建单命令,驱动状态机并触发履约。
 * <p>
 * TODO 生产增强:
 * 1. 建单改由 RocketMQ 事务消息 + 本地消息表,保证"建单与扣库存"不丢不重;
 * 2. Feign 扣库存建议前置到事务外或改异步 MQ,当前骨架为演示直接同步调用;
 * 3. 增加 15 分钟超时关单(延迟队列)与库存回滚对账任务;
 * 4. Sentinel/Seata 接入后,履约链路可切换为 Seata AT/TCC 强一致。
 */
@Slf4j
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final RocketMQTemplate rocketMQTemplate;

    public OrderService(OrderMapper orderMapper,
                        ProductClient productClient,
                        InventoryClient inventoryClient,
                        RocketMQTemplate rocketMQTemplate) {
        this.orderMapper = orderMapper;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 幂等处理秒抢建单命令。
     */
    @Transactional
    public void handleOrderCreate(SeckillOrderCommand cmd) {
        if (orderMapper.existsByRequestId(cmd.getRequestId())) {
            log.info("重复请求已忽略 requestId={}", cmd.getRequestId());
            return;
        }

        FlashOrder order = new FlashOrder();
        order.setOrderNo(IdGenerator.orderNo());
        order.setRequestId(cmd.getRequestId());
        order.setUserId(cmd.getUserId());
        order.setSkuId(cmd.getSkuId());
        order.setActivityId(cmd.getActivityId());
        order.setQuantity(cmd.getQuantity());

        if (!resolvePrice(order, cmd)) {
            order.setStatus(OrderStatus.FAILED);
            orderMapper.insert(order);
            log.warn("商品计价失败,订单标记 FAILED orderNo={} requestId={}", order.getOrderNo(), cmd.getRequestId());
            return;
        }

        order.setStatus(OrderStatus.INITIAL);
        orderMapper.insert(order);

        boolean deducted = deductStock(order, cmd);
        if (deducted) {
            order.setStatus(OrderStatus.CREATED);
            orderMapper.updateById(order);
            notifyFulfillment(order);
        } else {
            order.setStatus(OrderStatus.FAILED);
            orderMapper.updateById(order);
            log.warn("库存扣减失败,订单标记 FAILED orderNo={} requestId={}", order.getOrderNo(), cmd.getRequestId());
            // TODO 生产:对账任务回滚秒抢 Redis 预扣额度,并通知用户
        }
    }

    private boolean resolvePrice(FlashOrder order, SeckillOrderCommand cmd) {
        try {
            Result<SkuSellView> r = productClient.sellView(cmd.getSkuId());
            if (r == null || !r.isSuccess() || r.getData() == null) {
                log.warn("SKU 出售视图不可用 skuId={} result={}", cmd.getSkuId(), r);
                return false;
            }
            SkuSellView view = r.getData();
            if (view.getPrice() == null) {
                log.warn("SKU 缺少真实单价 skuId={}", cmd.getSkuId());
                return false;
            }
            if (view.getSkuStatus() == null || view.getSkuStatus() != 1) {
                log.warn("SKU 已下架不可售 skuId={} status={}", cmd.getSkuId(), view.getSkuStatus());
                return false;
            }
            order.setAmount(view.getPrice().multiply(BigDecimal.valueOf(cmd.getQuantity())));
            return true;
        } catch (Exception e) {
            log.error("调用商品服务计价异常 skuId={}", cmd.getSkuId(), e);
            return false;
        }
    }

    private boolean deductStock(FlashOrder order, SeckillOrderCommand cmd) {
        DeductStockCommand req = new DeductStockCommand(cmd.getRequestId(), order.getOrderNo(),
                cmd.getSkuId(), cmd.getQuantity());
        try {
            Result<DeductStockResult> r = inventoryClient.deduct(req);
            return r.isSuccess() && r.getData() != null && r.getData().isSuccess();
        } catch (Exception e) {
            log.error("调用库存服务异常 orderNo={}", order.getOrderNo(), e);
            return false;
        }
    }

    private void notifyFulfillment(FlashOrder order) {
        OrderFulfillEvent event = new OrderFulfillEvent();
        event.setOrderNo(order.getOrderNo());
        event.setUserId(order.getUserId());
        event.setSkuId(order.getSkuId());
        event.setQuantity(order.getQuantity());
        try {
            rocketMQTemplate.syncSend(MqTopics.ORDER_FULFILL + ":" + MqTopics.TAG_FULFILL, event, 3000);
            log.info("已发送履约事件 orderNo={}", order.getOrderNo());
        } catch (Exception e) {
            // TODO 生产:本地消息表重试,保证不丢单
            log.error("发送履约事件失败 orderNo={}", order.getOrderNo(), e);
        }
    }

    @Transactional
    public void markDispatched(String orderNo) {
        FlashOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在:" + orderNo);
        }
        if (!OrderStatus.CREATED.equals(order.getStatus())) {
            log.info("订单状态非法,忽略置发货 orderNo={} status={}", orderNo, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.DISPATCHED);
        orderMapper.updateById(order);
        log.info("订单已发货 orderNo={}", orderNo);
    }

    public FlashOrderView queryByRequestId(String requestId) {
        FlashOrder order = orderMapper.selectByRequestId(requestId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在,请稍后重试");
        }
        return toView(order);
    }

    public FlashOrderView queryByOrderNo(String orderNo) {
        FlashOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return toView(order);
    }

    private FlashOrderView toView(FlashOrder order) {
        FlashOrderView v = new FlashOrderView();
        v.setOrderNo(order.getOrderNo());
        v.setUserId(order.getUserId());
        v.setSkuId(order.getSkuId());
        v.setQuantity(order.getQuantity());
        v.setAmount(order.getAmount());
        v.setStatus(order.getStatus());
        return v;
    }
}
