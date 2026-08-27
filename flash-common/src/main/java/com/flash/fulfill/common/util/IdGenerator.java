package com.flash.fulfill.common.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量 ID 生成器(骨架版本)。
 * 生产环境建议替换为雪花算法或号段发号器,以保证全局唯一且递增。
 */
public final class IdGenerator {

    private static final AtomicLong SEQ = new AtomicLong(0);

    private IdGenerator() {
    }

    /** 幂等键:32 位无横线 UUID */
    public static String requestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 订单号:FF + 毫秒时间戳 + 4 位序号(演示用) */
    public static String orderNo() {
        long ts = System.currentTimeMillis();
        long seq = SEQ.incrementAndGet() % 10000;
        return "FF" + ts + String.format("%04d", seq);
    }
}