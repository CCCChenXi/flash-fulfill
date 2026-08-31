package com.flash.fulfill.fulfillment.constant;

/**
 * 履约服务私有常量。
 */
public final class FulfillmentConstant {

    /** 默认承运商编码 */
    public static final String CARRIER_CODE_DEFAULT = "SF-DEFAULT";

    /** 运单号前缀 */
    public static final String TRACKING_NO_PREFIX = "SF";

    /** 运单号取自订单号尾部截取的长度 */
    public static final int TRACKING_NO_SUFFIX_LENGTH = 12;

    /** 派单状态:已发货 */
    public static final String STATUS_DISPATCHED = "DISPATCHED";

    /** 派单状态:已送达 */
    public static final String STATUS_DELIVERED = "DELIVERED";

    private FulfillmentConstant() {
    }
}