package com.flash.fulfill.payment.service;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.payment.entity.PaymentOutbox;
import com.flash.fulfill.payment.feign.OrderClient;
import com.flash.fulfill.payment.mapper.PaymentOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 支付本地消息表(Outbox)轮询转发器。
 * <p>
 * 定时轮询 payment_outbox 中 PENDING 记录,Feign 回调订单 markPaid(orderNo),
 * 成功置 SENT,失败累加重试计数保留 PENDING 下轮重推。
 * <p>
 * selectPending 使用 FOR UPDATE SKIP LOCKED,多实例部署时同一条消息仅被一个实例处理。
 */
@Slf4j
@Component
public class PaymentOutboxRelay {

    private final PaymentOutboxMapper outboxMapper;
    private final OrderClient orderClient;

    @Value("${payment.outbox.batch-size:50}")
    private int batchSize;

    public PaymentOutboxRelay(PaymentOutboxMapper outboxMapper, OrderClient orderClient) {
        this.outboxMapper = outboxMapper;
        this.orderClient = orderClient;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:1000}")
    @Transactional
    public void relayPending() {
        List<PaymentOutbox> batch = outboxMapper.selectPending(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (PaymentOutbox record : batch) {
            try {
                Result<Boolean> r = orderClient.markPaid(record.getOrderNo());
                if (r != null && r.isSuccess()) {
                    outboxMapper.markSent(record.getId());
                    log.info("支付回调订单 markPaid 成功 payNo={} orderNo={}", record.getPayNo(), record.getOrderNo());
                } else {
                    outboxMapper.incrementRetry(record.getId());
                    log.warn("订单 markPaid 返回失败 payNo={} result={}", record.getPayNo(), r);
                }
            } catch (Exception e) {
                outboxMapper.incrementRetry(record.getId());
                log.error("回调订单 markPaid 异常 payNo={} orderNo={}", record.getPayNo(), record.getOrderNo(), e);
            }
        }
    }
}