package com.flash.fulfill.common.constant;

/**
 * 订单状态机。
 * <p>
 * INITIAL ->(用户确认订单:填地址,后端校验金额)→ PENDING_PAYMENT ->(支付成功)→ PENDING_SHIPMENT
 * ->(履约/仓库发货)→ SHIPPED ->(确认收货 / 超时自动确认)→ COMPLETED
 * <p>
 * 任意合适阶段可流转到 CANCELLED(取消)。INITIAL 同时作为订单行未落库(异步建单中)时的软状态。
 */
public final class OrderStatus {

    private OrderStatus() {
    }

    /** 初始状态(用户确认订单中 / 订单行未落库的软状态) */
    public static final String INITIAL = "INITIAL";
    /** 待支付 */
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    /** 待发货(已支付,等待履约发货) */
    public static final String PENDING_SHIPMENT = "PENDING_SHIPMENT";
    /** 已发货 */
    public static final String SHIPPED = "SHIPPED";
    /** 已完成 / 确认收货 */
    public static final String COMPLETED = "COMPLETED";
    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";
}