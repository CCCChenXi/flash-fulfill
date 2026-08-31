# 秒杀库存从 DB 加载到 Redis — 实施计划

**Goal:** 移除秒杀 Lua 中的默认库存兜底逻辑，改为由 flash-inventory 启动时把 DB 库存（`available > 0`）经 pipeline 加载到 Redis。

**Architecture:** 秒杀 Lua 库存 key 不存在时返回 4（商品不存在），不再用默认值初始化。新增 `StockCacheLoader`（ApplicationRunner）在库存服务启动时用 MyBatis-Plus QueryWrapper 查 `available > 0` 的 stock，pipeline 批量写入 `seckill:stock:{skuId}`（不设 TTL）。

**Date:** 2026-08-30

## Global Constraints

- 库存 key 不存在 → Lua 返回 4（商品不存在），`reqKey` 置 OFF_SHELF
- 删除 `seckill.stock.default`（配置 + `@Value` + Lua ARGV[2]）；Lua ARGV 重排为 `[qty, buyLimit, ttl]`
- 加载组件独立：`flash-inventory` 新增 `StockCacheLoader implements ApplicationRunner`
- 加载源：DB `stock.available`，仅 `available > 0`；用 pipeline；不设 TTL；覆盖写入（以 DB 为准）
- key 前缀用 `RedisKeys.SECKILL_STOCK_PREFIX`
- 保留 `StockDataInitializer`（DB 种子数据是 Redis 加载源）
- 本轮不做 Redis↔DB 双向同步/对账；运行中可用扣到 0 不删除 Redis key
- 查询方式：MyBatis-Plus `QueryWrapper.gt("available", 0)`（不改 Mapper/XML）

---

### Task 1: 秒杀 Lua 去默认库存
**Files:**
- Modify: `flash-seckill/src/main/resources/lua/seckill_prededuct.lua`

- [ ] **Step 1:** 删除 `local defaultStock = tonumber(ARGV[2])`；ARGV 注释重排为 `ARGV[1]=qty, ARGV[2]=buyLimit, ARGV[3]=ttl`
- [ ] **Step 2:** 库存校验段：`stock == false` → `SET reqKey OFF_SHELF` + `return 4`；不再 SET defaultStock

### Task 2: SeckillScriptExecutor 去默认库存
**Files:**
- Modify: `flash-seckill/.../script/SeckillScriptExecutor.java`

- [ ] **Step 1:** 删除 `@Value("${seckill.stock.default:100}") int defaultStock` 字段与构造器参数
- [ ] **Step 2:** `execute` 的 `redisTemplate.execute(script, keys, quantity, buyLimit, ttl)`（去掉 defaultStock）

### Task 3: 配置删除
**Files:**
- Modify: `flash-seckill/src/main/resources/application.yml`

- [ ] **Step 1:** 删除 `stock: default: 100` 块（`seckill.stock.default`）

### Task 4: 新增 StockCacheLoader
**Files:**
- Create: `flash-inventory/.../config/StockCacheLoader.java`
- Create: `flash-inventory/src/test/java/com/flash/fulfill/inventory/config/StockCacheLoaderTest.java`

- [ ] **Step 1:** `@Component implements ApplicationRunner`；注入 `StockMapper`、`StringRedisTemplate`
- [ ] **Step 2:** `run`：`List<Stock> list = stockMapper.selectList(new QueryWrapper<Stock>().gt("available", 0))`；`redisTemplate.executePipelined(RedisCallback)` 批量 `SET {SECKILL_STOCK_PREFIX}{skuId} available`（不设 TTL）
- [ ] **Step 3:** 单测（mock mapper + mock redisTemplate，断言 executePipelined 被调）
- [ ] **Step 4:** 编译 `mvn -q -pl flash-inventory,flash-seckill -am -DskipTests package`

### Task 5: 测试与全量编译
**Files:**
- Modify: `flash-seckill/src/test/java/com/flash/fulfill/seckill/script/SeckillScriptExecutorTest.java`

- [ ] **Step 1:** 调整 `SeckillScriptExecutorTest` 的 execute 调用（移除 defaultStock 参数）
- [ ] **Step 2:** `mvn -q -DskipTests package`