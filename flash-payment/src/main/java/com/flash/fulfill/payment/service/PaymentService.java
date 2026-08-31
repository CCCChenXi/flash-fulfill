package com.flash.fulfill.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.common.util.IdGenerator;
import com.flash.fulfill.payment.constant.PaymentStatus;
import com.flash.fulfill.payment.dto.PaymentCallbackCommand;
import com.flash.fulfill.payment.dto.PaymentCreateCommand;
import com.flash.fulfill.payment.dto.PaymentView;
import com.flash.fulfill.payment.entity.PaymentOutbox;
import com.flash.fulfill.payment.entity.PaymentRecord;
import com.flash.fulfill.payment.mapper.PaymentOutboxMapper;
import com.flash.fulfill.payment.mapper.PaymentRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 支付服务:发起支付(幂等)+ 模拟网关回调 + Outbox 可靠回调订单。
 * <p>
 * 支付成功与写本地消息表同事务;PaymentOutboxRelay 轮询重推 markPaid,
 * 保证订单从 PENDING_PAYMENT 流转到 PENDING_SHIPMENT 并触发履约。
 */
@Slf4j
@Service
public class PaymentService {

    public static final String OUTBOX_STATUS_PENDING = "PENDING";
    public static final String MSG_TYPE_MARK_PAID = "MARK_PAID";
    public static final String CHANNEL_DEFAULT = "MOCK";

    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentOutboxMapper paymentOutboxMapper;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRecordMapper paymentRecordMapper,
                          PaymentOutboxMapper paymentOutboxMapper,
                          ObjectMapper objectMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentOutboxMapper = paymentOutboxMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起支付:同一订单只建一张支付单(uk_order_no 幂等),返回 INITIAL 待支付。
     */
    @Transactional
    public PaymentView createPayment(PaymentCreateCommand cmd) {
        PaymentRecord existing = paymentRecordMapper.selectByOrderNo(cmd.getOrderNo());
        if (existing != null) {
            log.info("该订单已存在支付单,幂等返回 payNo={} orderNo={}", existing.getPayNo(), cmd.getOrderNo());
            return toView(existing);
        }
        PaymentRecord record = new PaymentRecord();
        record.setPayNo(IdGenerator.payNo());
        record.setOrderNo(cmd.getOrderNo());
        record.setUserId(cmd.getUserId());
        record.setAmount(cmd.getAmount());
        record.setChannel(cmd.getChannel() == null ? CHANNEL_DEFAULT : cmd.getChannel());
        record.setStatus(PaymentStatus.INITIAL);
        paymentRecordMapper.insert(record);
        log.info("已创建支付单 payNo={} orderNo={} amount={}", record.getPayNo(), cmd.getOrderNo(), record.getAmount());
        return toView(record);
    }

    /**
     * 支付网关异步回调:成功置 PAID 并写 MARK_PAID Outbox;失败置 FAILED 不回写;已 PAID 幂等忽略。
     */
    @Transactional
    public PaymentView handleCallback(PaymentCallbackCommand cmd) {
        PaymentRecord record = paymentRecordMapper.selectByPayNo(cmd.getPayNo());
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付单不存在:" + cmd.getPayNo());
        }
        if (PaymentStatus.PAID.equals(record.getStatus())) {
            log.info("支付单已支付,幂等忽略 payNo={}", cmd.getPayNo());
            return toView(record);
        }
        record.setStatus(Boolean.TRUE.equals(cmd.getSuccess()) ? PaymentStatus.PAID : PaymentStatus.FAILED);
        paymentRecordMapper.updateById(record);
        if (PaymentStatus.PAID.equals(record.getStatus())) {
            writeMarkPaidOutbox(record);
        }
        log.info("支付回调处理完成 payNo={} status={}", cmd.getPayNo(), record.getStatus());
        return toView(record);
    }

    public PaymentView queryByOrderNo(String orderNo) {
        PaymentRecord record = paymentRecordMapper.selectByOrderNo(orderNo);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付单不存在:" + orderNo);
        }
        return toView(record);
    }

    private void writeMarkPaidOutbox(PaymentRecord record) {
        PaymentOutbox outbox = new PaymentOutbox();
        outbox.setPayNo(record.getPayNo());
        outbox.setOrderNo(record.getOrderNo());
        outbox.setMsgType(MSG_TYPE_MARK_PAID);
        outbox.setMsgBody(toJson(Map.of("orderNo", record.getOrderNo())));
        outbox.setStatus(OUTBOX_STATUS_PENDING);
        outbox.setRetryCount(0);
        paymentOutboxMapper.insert(outbox);
    }

    private PaymentView toView(PaymentRecord record) {
        PaymentView v = new PaymentView();
        v.setPayNo(record.getPayNo());
        v.setOrderNo(record.getOrderNo());
        v.setAmount(record.getAmount());
        v.setChannel(record.getChannel());
        v.setStatus(record.getStatus());
        return v;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox 载荷序列化失败", e);
        }
    }
}