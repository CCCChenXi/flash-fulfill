package com.flash.fulfill.common.constant;

/**
 * RocketMQ 主题 / 标签 / 消费者组常量。
 */
public final class MqTopics {

    private MqTopics() {
    }

    /** 秒抢 -> 订单:创建订单命令 */
    public static final String FLASH_ORDER_CREATE = "FLASH_ORDER_CREATE";
    public static final String TAG_ORDER_CREATE = "ORDER_CREATE";

    /** 订单 -> 履约:需要派单的事件 */
    public static final String ORDER_FULFILL = "ORDER_FULFILL";
    public static final String TAG_FULFILL = "FULFILL";

    /** 消费者组 */
    public static final String GROUP_ORDER_CREATE_CONSUMER = "flash-order-create-consumer";
    public static final String GROUP_FULFILL_CONSUMER = "flash-order-fulfill-consumer";
}