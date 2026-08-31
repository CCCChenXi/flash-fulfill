package com.flash.fulfill.order.config;

import com.flash.fulfill.common.constant.OrderStatus;
import com.flash.fulfill.order.entity.FlashOrder;
import com.flash.fulfill.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时任务:
 * <ol>
 *   <li>超时关单:超过 N 分钟仍未支付(INITIAL/PENDING_PAYMENT)的订单置 CANCELLED;</li>
 *   <li>超时自动完成:已发货(SHIPPED)超过 N 天未确认收货的订单置 COMPLETED。</li>
 * </ol>
 * <p>
 * TODO 生产:超时关单建议用 RocketMQ 延迟消息精确触发 + 对账补偿;骨架版用定时扫描。
 */
@Slf4j
@Component
public class OrderTimeoutCanceler {

    private final OrderMapper orderMapper;

    @Value("${order.timeout-cancel.minutes:15}")
    private int timeoutMinutes;

    @Value("${order.auto-complete.days:7}")
    private int autoCompleteDays;

    @Value("${order.timeout-cancel.batch-size:100}")
    private int batchSize;

    public OrderTimeoutCanceler(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Scheduled(fixedDelayString = "${order.timeout-cancel.poll-interval-ms:60000}")
    @Transactional
    public void cancelExpired() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<FlashOrder> expired = orderMapper.selectExpiredPending(expireTime, batchSize);
        if (expired.isEmpty()) {
            return;
        }
        for (FlashOrder order : expired) {
            order.setStatus(OrderStatus.CANCELLED);
            orderMapper.updateById(order);
            log.info("订单超时未支付已取消 orderNo={} timeoutMinutes={}", order.getOrderNo(), timeoutMinutes);
        }
    }

    @Scheduled(fixedDelayString = "${order.auto-complete.poll-interval-ms:3600000}")
    @Transactional
    public void autoCompleteShipped() {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(autoCompleteDays);
        List<FlashOrder> expired = orderMapper.selectExpiredShipped(expireTime, batchSize);
        if (expired.isEmpty()) {
            return;
        }
        for (FlashOrder order : expired) {
            order.setStatus(OrderStatus.COMPLETED);
            orderMapper.updateById(order);
            log.info("订单超时自动确认收货 orderNo={} autoCompleteDays={}", order.getOrderNo(), autoCompleteDays);
        }
    }
}