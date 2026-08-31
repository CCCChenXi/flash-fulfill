package com.flash.fulfill.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 轻量雪花算法 ID 生成器(骨架版)。
 * <p>
 * 64 位分布:1bit 符号位 + 41bit 时间戳(自 2020-01-01 毫秒) + 5bit 数据中心 + 5bit 机器 + 12bit 序列号。
 * datacenterId 恒为 0(单机房);workerId 由进程启动时按本机 IP 哈希 + 随机探针推导,
 * 保证同项目多实例各自不同,零第三方依赖,符合 flash-common 轻量约束。
 * <p>
 * 时钟回拨保护:发现 ts < lastTs 时自旋等待直至追上,避免产生重复 ID。
 * 生产如需多机房/超大集群,可改为注册中心(Nacos)下发 workerId,框架不变。
 */
public final class IdGenerator {

    /** 起始纪元:2020-01-01 00:00:00 UTC 毫秒 */
    private static final long EPOCH = 1577836800000L;

    private static final int DATACENTER_ID_BITS = 5;
    private static final int WORKER_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long DATACENTER_ID = 0L;

    private static final long WORKER_ID = resolveWorkerId();

    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    private IdGenerator() {
    }

    /** 下一毫秒时间的工具:阻塞直到系统时钟越过 lastTimestamp */
    private static long tilNextMillis(long lastTimestamp) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTimestamp) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }

    /** 进程内推导 workerId:本机 IP 末段 + 随机探针,保证不同实例不同 */
    private static long resolveWorkerId() {
        try {
            byte[] ip = InetAddress.getLocalHost().getAddress();
            long ipHash = 0L;
            for (byte b : ip) {
                ipHash = (ipHash << 8) | (b & 0xFF);
            }
            long probe = (long) (Math.random() * 31);
            return (ipHash + probe) & MAX_WORKER_ID;
        } catch (UnknownHostException e) {
            return (long) (Math.random() * 32);
        }
    }

    /** 生成下一个雪花 ID */
    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            // 时钟回拨保护:等待追上再生成,避免重复
            timestamp = tilNextMillis(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 本毫秒已用满 4096 个,阻塞到下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (DATACENTER_ID << DATACENTER_ID_SHIFT)
                | (WORKER_ID << WORKER_ID_SHIFT)
                | sequence;
    }

    /** 幂等键:雪花 long 的字符串形式(全局唯一且递增) */
    public static String requestId() {
        return String.valueOf(nextId());
    }

    /** 订单号:FF + 雪花 long(保留前缀便于识别) */
    public static String orderNo() {
        return "FF" + nextId();
    }

    /** 支付单号:PAY + 雪花 long */
    public static String payNo() {
        return "PAY" + nextId();
    }
}
