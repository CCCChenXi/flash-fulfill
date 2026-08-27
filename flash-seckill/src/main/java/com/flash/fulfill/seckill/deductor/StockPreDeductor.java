package com.flash.fulfill.seckill.deductor;

/**
 * 秒抢库存预扣策略。
 * <p>
 * TODO 生产实现建议:Redis Lua 脚本原子执行"校验 + 扣减 + 兜底初始化"与"回滚",
 * 以百微秒级吞吐支撑 10W+ QPS 限峰值预扣。
 */
public interface StockPreDeductor {

    /**
     * 预扣库存,成功返回 true,不足返回 false。
     */
    boolean tryPreDeduct(Long skuId, int quantity);

    /**
     * 回滚预扣库存(下单失败/取消时调用)。
     */
    void rollback(Long skuId, int quantity);
}