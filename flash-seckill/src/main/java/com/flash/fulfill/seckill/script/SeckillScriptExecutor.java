package com.flash.fulfill.seckill.script;

import com.flash.fulfill.common.constant.RedisKeys;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 秒杀幂等状态机 + 限购 + 价格校验 + 预扣 + 建单事件脚本执行器。
 * <p>
 * 一次 Redis 往返完成:请求幂等占位(四态记忆)、SPU/SKU 状态校验、限购计数、价格校验、库存原子扣减,
 * 并在扣减成功后将建单事件以多 field-value 形式 XADD 到 Redis Stream(与扣减同一次 EVAL,保证原子性)。
 * 价格由脚本从 {@code seckill:price:{skuId}} 读取后追加写入事件,不再拼 JSON。
 * 事件由独立 flash-relay 服务消费并转发 RocketMQ,请求线程不再同步发送。
 */
@Slf4j
@Component
public class SeckillScriptExecutor {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public SeckillScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        try {
            ClassPathResource resource = new ClassPathResource("lua/seckill_prededuct.lua");
            String lua = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            this.script = new DefaultRedisScript<>(lua, Long.class);
        } catch (Exception e) {
            throw new IllegalStateException("加载秒杀 Lua 脚本失败", e);
        }
    }

    /**
     * 原子幂等校验 + 限购 + 价格校验 + 预扣 + 写建单事件(多字段,含价格)。
     *
     * @return 0 成功 / 1 参数非法 / 2 下架 / 3 库存不足 / 4 商品或库存不存在 / 5 处理中 / 6 限购 / 7 价格未就绪
     */
    public int execute(SeckillOrderCommand cmd, int buyLimit, int ttlSeconds) {
        List<String> keys = List.of(
                RedisKeys.SECKILL_REQ_PREFIX + cmd.getRequestId(),
                RedisKeys.SECKILL_STOCK_PREFIX + cmd.getSkuId(),
                RedisKeys.SECKILL_SKU_STATUS_PREFIX + cmd.getSkuId(),
                RedisKeys.SECKILL_SPU_STATUS_PREFIX + cmd.getSpuId(),
                RedisKeys.SECKILL_BUY_PREFIX + cmd.getUserId() + RedisKeys.BUY_KEY_SEPARATOR
                        + cmd.getActivityId() + RedisKeys.BUY_KEY_SEPARATOR + cmd.getSkuId(),
                RedisKeys.SECKILL_EVENT_STREAM,
                RedisKeys.SECKILL_PRICE_PREFIX + cmd.getSkuId());

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(cmd.getQuantity()));
        args.add(String.valueOf(buyLimit));
        args.add(String.valueOf(ttlSeconds));
        addField(args, "requestId", cmd.getRequestId());
        addField(args, "userId", cmd.getUserId() == null ? null : String.valueOf(cmd.getUserId()));
        addField(args, "skuId", cmd.getSkuId() == null ? null : String.valueOf(cmd.getSkuId()));
        addField(args, "spuId", cmd.getSpuId() == null ? null : String.valueOf(cmd.getSpuId()));
        addField(args, "activityId", cmd.getActivityId() == null ? null : String.valueOf(cmd.getActivityId()));
        addField(args, "quantity", String.valueOf(cmd.getQuantity()));

        Long result = redisTemplate.execute(script, keys, args.toArray());
        if (result == null) {
            log.warn("秒杀脚本执行返回为空 requestId={} skuId={} spuId={}",
                    cmd.getRequestId(), cmd.getSkuId(), cmd.getSpuId());
            return -1;
        }
        return result.intValue();
    }

    private void addField(List<String> args, String field, String value) {
        args.add(field);
        args.add(value == null ? "" : value);
    }
}