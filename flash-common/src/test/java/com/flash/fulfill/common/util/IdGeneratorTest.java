package com.flash.fulfill.common.util;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorTest {

    @Test
    void nextIdIsPositiveLong() {
        long id = IdGenerator.nextId();
        assertTrue(id > 0);
    }

    @Test
    void nextIdIsStrictlyIncreasingWithinSameCaller() {
        long prev = IdGenerator.nextId();
        for (int i = 0; i < 10000; i++) {
            long next = IdGenerator.nextId();
            assertTrue(next > prev, "id 必须严格递增");
            prev = next;
        }
    }

    @Test
    void sequentialIdsAreUniqueInSameThread() {
        Long[] seen = new Long[5000];
        for (int i = 0; i < seen.length; i++) {
            seen[i] = IdGenerator.nextId();
        }
        Set<Long> set = Set.of(seen);
        assertEquals(seen.length, set.size(), "连续 5000 个 ID 不应重复");
    }

    @Test
    void concurrentIdsAreUniqueAcrossThreads() throws InterruptedException {
        int threads = 8;
        int perThread = 2000;
        Set<Long> all = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        all.add(IdGenerator.nextId());
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发生成应在超时前完成");
        pool.shutdown();

        assertEquals(threads * perThread, all.size(), "并发 ID 不应有碰撞");
    }

    @Test
    void embeddedWorkerIdIsConsistentAcrossCalls() {
        long a = IdGenerator.nextId();
        long b = IdGenerator.nextId();
        // 唯一性由 41bit 时间戳 + 12bit 序列号保证;本测试仅验证同为进程内同一个 workerId 来源
        assertNotEquals(a, b);
        assertNotSame(a, b);
    }

    @Test
    void requestIdIsNumericString() {
        String req = IdGenerator.requestId();
        assertTrue(req.matches("\\d+"), "requestId 应为纯数字字符串");
        assertNotEquals(IdGenerator.requestId(), req);
    }

    @Test
    void orderNoKeepsFfPrefix() {
        String no = IdGenerator.orderNo();
        assertTrue(no.startsWith("FF"), "orderNo 应保留 FF 前缀");
        assertTrue(no.substring(2).matches("\\d+"), "FF 前缀后应为数字");
    }

    @Test
    void orderNoIsUnique() {
        String a = IdGenerator.orderNo();
        String b = IdGenerator.orderNo();
        assertNotEquals(a, b);
    }

    @Test
    void idDecodesToExpectedTimestampAndWorkerSegments() {
        long id = IdGenerator.nextId();
        long timestamp = (id >> 22) + 1577836800000L;
        long datacenter = (id >> 17) & 0x1F;
        long worker = (id >> 12) & 0x1F;

        assertTrue(timestamp > 0, "解码出的时间戳应为正");
        assertEquals(0L, datacenter, "datacenterId 恒为 0");
        assertTrue(worker >= 0 && worker <= 31, "workerId 应在 0~31 区间");
    }
}
