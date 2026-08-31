-- 秒杀入口幂等状态机 + 限购 + 价格校验 + 预扣 + 事件写入脚本
-- KEYS[1] = 幂等状态 key (seckill:req:{requestId})
-- KEYS[2] = 库存 key (seckill:stock:{skuId})
-- KEYS[3] = SKU 状态 key (seckill:sku:status:{skuId})
-- KEYS[4] = SPU 状态 key (seckill:spu:status:{spuId})
-- KEYS[5] = 限购计数 key (seckill:buy:{userId}:{activityId}:{skuId})
-- KEYS[6] = 建单事件 Stream key (seckill:event:order)
-- KEYS[7] = 秒杀价格 key (seckill:price:{skuId})
-- ARGV[1] = 购买数量
-- ARGV[2] = 限购上限
-- ARGV[3] = 幂等状态 TTL(秒)
-- ARGV[4..] = 建单事件字段对 (field1, value1, field2, value2, ...)
--             字段: requestId / userId / skuId / spuId / activityId / quantity(price 由脚本从 Redis 读取后追加)
-- 返回: 0 成功 / 1 参数非法 / 2 下架 / 3 库存不足 / 4 商品或库存不存在 / 5 处理中 / 6 限购 / 7 价格未就绪

local reqKey = KEYS[1]
local stockKey = KEYS[2]
local skuStatusKey = KEYS[3]
local spuStatusKey = KEYS[4]
local buyKey = KEYS[5]
local eventKey = KEYS[6]
local priceKey = KEYS[7]

local qty = tonumber(ARGV[1])
local buyLimit = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

if qty == nil or qty <= 0 then
    return 1
end

-- 尝试占位:SET NX,避免并发重复
local ok = redis.call('SET', reqKey, 'PROCESSING', 'NX', 'EX', ttl)
if ok == false then
    -- 占位失败:读已有状态返回
    local existing = redis.call('GET', reqKey)
    if existing == 'SUCCESS' then
        return 0
    elseif existing == 'LIMIT' then
        return 6
    elseif existing == 'OFF_SHELF' then
        return 2
    elseif existing == 'STOCK' then
        return 3
    elseif existing == 'PRICE' then
        return 7
    else
        return 5
    end
end

-- 已占位成功,继续校验商品状态
local skuStatus = redis.call('GET', skuStatusKey)
local spuStatus = redis.call('GET', spuStatusKey)
if skuStatus == false or spuStatus == false then
    redis.call('SET', reqKey, 'OFF_SHELF', 'EX', ttl)
    return 4
end
if skuStatus ~= '1' or spuStatus ~= '1' then
    redis.call('SET', reqKey, 'OFF_SHELF', 'EX', ttl)
    return 2
end

-- 限购校验:计数 +1 后判断,超限回退计数并写 LIMIT
local bought = redis.call('INCR', buyKey)
if bought > buyLimit then
    redis.call('DECR', buyKey)
    redis.call('SET', reqKey, 'LIMIT', 'EX', ttl)
    return 6
end

-- 库存校验:库存 key 由 flash-inventory 启动时从 DB 加载,缺失视为商品不存在
local stock = redis.call('GET', stockKey)
if stock == false then
    redis.call('DECR', buyKey)
    redis.call('SET', reqKey, 'OFF_SHELF', 'EX', ttl)
    return 4
end
local left = tonumber(stock)
if left < qty then
    redis.call('DECR', buyKey)
    redis.call('SET', reqKey, 'STOCK', 'EX', ttl)
    return 3
end

-- 价格校验(扣库存前):价格 key 由商品服务维护,缺失/非法视为价格未就绪
local price = redis.call('GET', priceKey)
if price == false then
    redis.call('DECR', buyKey)
    redis.call('SET', reqKey, 'PRICE', 'EX', ttl)
    return 7
end
local priceNum = tonumber(price)
if priceNum == nil or priceNum <= 0 then
    redis.call('DECR', buyKey)
    redis.call('SET', reqKey, 'PRICE', 'EX', ttl)
    return 7
end

-- 扣减库存 + 写建单事件到 Stream(多 field-value,价格由脚本追加,不拼 JSON)+ 标记成功态
redis.call('DECRBY', stockKey, qty)
local eventFields = {}
for i = 4, #ARGV do
    eventFields[#eventFields + 1] = ARGV[i]
end
eventFields[#eventFields + 1] = 'price'
eventFields[#eventFields + 1] = tostring(priceNum)
redis.call('XADD', eventKey, '*', unpack(eventFields))
redis.call('SET', reqKey, 'SUCCESS', 'EX', ttl)
return 0