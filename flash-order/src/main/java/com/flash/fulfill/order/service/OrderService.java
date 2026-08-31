package com.flash.fulfill.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.constant.OrderStatus;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.dto.FlashOrderView;
import com.flash.fulfill.common.dto.OrderFulfillEvent;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.common.util.IdGenerator;
import com.flash.fulfill.order.entity.FlashOrder;
import com.flash.fulfill.order.entity.OrderAddress;
import com.flash.fulfill.order.entity.OrderOutbox;
import com.flash.fulfill.order.dto.OrderConfirmCommand;
import com.flash.fulfill.order.mapper.OrderAddressMapper;
import com.flash.fulfill.order.mapper.OrderMapper;
import com.flash.fulfill.order.mapper.OrderOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 订单服务:消息建单(事务性 Outbox)+ 状态机流转。
 * <p>
 * 状态机:INITIAL ->(确认订单填地址)PENDING_PAYMENT ->(支付)PENDING_SHIPMENT -> SHIPPED -> COMPLETED;合适阶段 -> CANCELLED。
 * 建单落地 INITIAL,同时写 DEDUCT 本地消息表(同事务);商品价格由秒杀 Lua 从 Redis 读取后随消息传入,
 * 订单侧直接以消息内 price 计价,不自取价格。
 * 履约事件(FULFILL)在支付成功后写入 Outbox 可靠投递。库存扣减不回写订单状态。
 */
@Slf4j
@Service
public class OrderService {

    public static final String OUTBOX_STATUS_PENDING = "PENDING";
    public static final String MSG_TYPE_DEDUCT = "DEDUCT";
    public static final String MSG_TYPE_FULFILL = "FULFILL";

    private final OrderMapper orderMapper;
    private final OrderOutboxMapper orderOutboxMapper;
    private final OrderAddressMapper orderAddressMapper;
    private final ObjectMapper objectMapper;

    public OrderService(OrderMapper orderMapper,
                        OrderOutboxMapper orderOutboxMapper,
                        OrderAddressMapper orderAddressMapper,
                        ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderOutboxMapper = orderOutboxMapper;
        this.orderAddressMapper = orderAddressMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 建单:订单表(INITIAL)+ 本地消息表(DEDUCT)同一事务写入。
     * 价格以消息内 price 为准;缺失/非法抛异常走 MQ 重试。
     * 收货地址不在秒杀命令内,由客户端在订单详情页填写后经确认接口(confirmOrderInfo)写入。
     * 重复消息由 uk_request_id 唯一键拦截,消费者捕获 DuplicateKeyException 忽略。
     */
    @Transactional
    public void handleOrderCreate(SeckillOrderCommand cmd) {
        BigDecimal price = cmd.getPrice();
        if (price == null || price.signum() <= 0) {
            throw new IllegalStateException("建单消息缺少有效价格 requestId=" + cmd.getRequestId());
        }

        FlashOrder order = new FlashOrder();
        order.setOrderNo(IdGenerator.orderNo());
        order.setRequestId(cmd.getRequestId());
        order.setUserId(cmd.getUserId());
        order.setSkuId(cmd.getSkuId());
        order.setActivityId(cmd.getActivityId());
        order.setQuantity(cmd.getQuantity());
        order.setAmount(price.multiply(BigDecimal.valueOf(cmd.getQuantity())));
        order.setStatus(OrderStatus.INITIAL);
        orderMapper.insert(order);

        OrderOutbox outbox = new OrderOutbox();
        outbox.setRequestId(cmd.getRequestId());
        outbox.setOrderNo(order.getOrderNo());
        outbox.setMsgType(MSG_TYPE_DEDUCT);
        outbox.setMsgBody(toJson(new DeductStockCommand(
                cmd.getRequestId(), order.getOrderNo(), cmd.getSkuId(), cmd.getQuantity())));
        outbox.setStatus(OUTBOX_STATUS_PENDING);
        outbox.setRetryCount(0);
        orderOutboxMapper.insert(outbox);
        log.info("订单已创建 orderNo={} status=INITIAL requestId={}", order.getOrderNo(), cmd.getRequestId());
    }

    /**
     * 确认订单(客户端填地址后提交):同步事务内更新订单状态并写入收货地址表。
     * INITIAL -> PENDING_PAYMENT;重复提交幂等,地址按订单 upsert。
     */
    @Transactional
    public void confirmOrderInfo(String orderNo, OrderConfirmCommand cmd) {
        FlashOrder order = requireOrder(orderNo);
        if (!OrderStatus.INITIAL.equals(order.getStatus())
                && !OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            log.info("订单状态不可确认,忽略 orderNo={} status={}", orderNo, order.getStatus());
            return;
        }
        if (OrderStatus.INITIAL.equals(order.getStatus())) {
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            orderMapper.updateById(order);
        }

        OrderAddress address = orderAddressMapper.selectByOrderId(order.getId());
        if (address == null) {
            address = new OrderAddress();
            address.setOrderId(order.getId());
            address.setReceiverName(cmd.getReceiverName());
            address.setReceiverPhone(cmd.getReceiverPhone());
            address.setProvince(cmd.getProvince());
            address.setCity(cmd.getCity());
            address.setDistrict(cmd.getDistrict());
            address.setDetailAddress(cmd.getDetailAddress());
            orderAddressMapper.insert(address);
        } else {
            address.setReceiverName(cmd.getReceiverName());
            address.setReceiverPhone(cmd.getReceiverPhone());
            address.setProvince(cmd.getProvince());
            address.setCity(cmd.getCity());
            address.setDistrict(cmd.getDistrict());
            address.setDetailAddress(cmd.getDetailAddress());
            orderAddressMapper.updateById(address);
        }
        log.info("订单已确认收货地址 orderNo={} status=PENDING_PAYMENT", orderNo);
    }

    /** 支付成功回调 PENDING_PAYMENT -> PENDING_SHIPMENT,并写履约事件到 Outbox。 */
    @Transactional
    public void markPaid(String orderNo) {
        FlashOrder order = requireOrder(orderNo);
        if (!OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            log.info("订单状态非法,忽略支付 orderNo={} status={}", orderNo, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.PENDING_SHIPMENT);
        orderMapper.updateById(order);
        writeFulfillOutbox(order);
        log.info("订单已支付,待发货 orderNo={}", orderNo);
    }

    /** 履约/仓库发货回调 PENDING_SHIPMENT -> SHIPPED */
    @Transactional
    public void markDispatched(String orderNo) {
        FlashOrder order = requireOrder(orderNo);
        if (!OrderStatus.PENDING_SHIPMENT.equals(order.getStatus())) {
            log.info("订单状态非法,忽略置发货 orderNo={} status={}", orderNo, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.SHIPPED);
        orderMapper.updateById(order);
        log.info("订单已发货 orderNo={}", orderNo);
    }

    /** 确认收货/超时自动确认 SHIPPED -> COMPLETED */
    @Transactional
    public void markCompleted(String orderNo) {
        FlashOrder order = requireOrder(orderNo);
        if (!OrderStatus.SHIPPED.equals(order.getStatus())) {
            log.info("订单状态非法,忽略完成 orderNo={} status={}", orderNo, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.COMPLETED);
        orderMapper.updateById(order);
        log.info("订单已完成 orderNo={}", orderNo);
    }

    /** 取消订单:初始/待支付/待发货 -> CANCELLED */
    @Transactional
    public void cancel(String orderNo) {
        FlashOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || !cancellable(order.getStatus())) {
            log.info("订单不存在或不可取消 orderNo={}", orderNo);
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderMapper.updateById(order);
        log.info("订单已取消 orderNo={}", orderNo);
    }

    private boolean cancellable(String status) {
        return OrderStatus.INITIAL.equals(status)
                || OrderStatus.PENDING_PAYMENT.equals(status)
                || OrderStatus.PENDING_SHIPMENT.equals(status);
    }

    private void writeFulfillOutbox(FlashOrder order) {
        OrderFulfillEvent event = new OrderFulfillEvent();
        event.setOrderNo(order.getOrderNo());
        event.setUserId(order.getUserId());
        event.setSkuId(order.getSkuId());
        event.setQuantity(order.getQuantity());

        OrderOutbox outbox = new OrderOutbox();
        outbox.setRequestId(order.getRequestId());
        outbox.setOrderNo(order.getOrderNo());
        outbox.setMsgType(MSG_TYPE_FULFILL);
        outbox.setMsgBody(toJson(event));
        outbox.setStatus(OUTBOX_STATUS_PENDING);
        outbox.setRetryCount(0);
        orderOutboxMapper.insert(outbox);
    }

    /**
     * 客户端凭 requestId 轮询:订单行尚未落库(异步建单中)返回 INITIAL 软状态。
     */
    public FlashOrderView queryByRequestId(String requestId) {
        FlashOrder order = orderMapper.selectByRequestId(requestId);
        if (order == null) {
            FlashOrderView pending = new FlashOrderView();
            pending.setStatus(OrderStatus.INITIAL);
            return pending;
        }
        return toView(order);
    }

    public FlashOrderView queryByOrderNo(String orderNo) {
        return toView(requireOrder(orderNo));
    }

    private FlashOrder requireOrder(String orderNo) {
        FlashOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在:" + orderNo);
        }
        return order;
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

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("本地消息表载荷序列化失败", e);
        }
    }
}