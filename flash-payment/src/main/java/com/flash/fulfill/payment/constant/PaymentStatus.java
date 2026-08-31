package com.flash.fulfill.payment.constant;

/**
 * 支付单状态。
 * INITIAL ->(支付成功)→ PAID;INITIAL ->(支付失败)→ FAILED;INITIAL/PAID ->(取消/退款预留)→ CANCELLED。
 */
public final class PaymentStatus {

    private PaymentStatus() {
    }

    /** 待支付 */
    public static final String INITIAL = "INITIAL";
    /** 支付成功 */
    public static final String PAID = "PAID";
    /** 支付失败 */
    public static final String FAILED = "FAILED";
    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";
}