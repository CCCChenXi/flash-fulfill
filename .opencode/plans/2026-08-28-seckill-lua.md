# 秒杀入口 Lua 原子校验+预扣 改造 — 实施计划

**Goal:** 将秒杀入口从「Feign 同步查商品状态 + 分开的 Redis 预扣」改为「一次 Redis Lua 脚本原子完成 SPU/SKU 状态校验 + 库存判断 + 扣减」，消除秒杀链路的网络 IO 卡顿；扣减成功后发 MQ 建单，再由 order 侧 Feign 扣数据库库存。

**Architecture:** 秒杀入口不再走 Feign。新增单条 Lua 脚本读取 `seckill:spu:status:{spuId}`、`seckill:sku:status:{skuId}`、`seckill:stock:{skuId}`，全满足则原子扣减并返回业务状态码；SeckillService 按状态码分派。flash-product 在 SPU/SKU 变更后同步写这对轻量 status key。

**Tech Stack:** Java 21 / Spring Boot 3.3.5 / Spring Cloud Alibaba / Redis(Spring Data Redis 的 DefaultRedisScript) / RocketMQ / 现有 MyBatis 链。无新增依赖。

**Date:** 2026-08-28

## Global Constraints

- Java 21；Spring Boot 3.3.5；Spring Cloud Alibaba 2023.0.3.2
- 对外接口：HTTP 恒 200；顶层 `Result.code` 不变；秒杀业务结果放在 `FlashOrderResponse.status`（int）
- 状态码约定：`0=成功` `1=参数非法` `2=商品下架` `3=库存不足` `4=商品或库存不存在`
- Redis key：状态 `seckill:spu:status:{spuId}` / `seckill:sku:status:{skuId}`（String "1"/"0"）；库存沿用 `seckill:stock:{skuId}`
- Lua 幂等：库存 key 若不存在则用 `seckill.stock.default`(默认100) 初始化后再判断；状态 key 缺失或值非 "1" 按相应码返回
- 秒杀入口不再使用 `ProductClient`（删除 `validateSellable` 与相关注入）；order 侧计价仍保留 `ProductClient`/`SkuSellView`
- 保留 Rollback：Lua 扣减成功但发 MQ 失败时回滚 Redis 库存
- 删除旧 `StockPreDeductor` 接口与 `RedisStockPreDeductor` 实现；新增 `SeckillScriptExecutor`
- 状态 key 由 flash-product 在 SPU/SKU「新建 / 更新 / 上下架」后同步写入（与现有 `evictAfterCommit` 同一时机）
- 单测用 Mockito / 直测 Lua 逻辑，无需中间件

---

### Task 1: 秒杀业务状态码枚举与响应字段

**Files:**
- Create: `flash-common/src/main/java/com/flash/fulfill/common/constant/SeckillResultCode.java`
- Modify: `flash-common/src/main/java/com/flash/fulfill/common/dto/FlashOrderResponse.java`（新增 `Integer status` 字段）

**Interfaces:**
- Produces: `SeckillResultCode.SUCCESS=0, INVALID_PARAM=1, OFF_SHELF=2, STOCK_NOT_ENOUGH=3, NOT_EXIST=4`
- `FlashOrderResponse` 增加 `getStatus()/setStatus(Integer)`，保留两个旧构造器，新增三参构造

- [ ] **Step 1: `SeckillResultCode.java`**
- [ ] **Step 2: `FlashOrderResponse.java` 增加 `status`**
- [ ] **Step 3: 验证** `mvn -q -pl flash-common -am test`
- [ ] **Step 4: 提交** `feat(seckill): 秒杀业务状态码与响应字段`

---

### Task 2: Lua 脚本 + SeckillScriptExecutor

**Files:**
- Create: `flash-seckill/src/main/resources/lua/seckill_prededuct.lua`
- Create: `flash-seckill/src/main/java/com/flash/fulfill/seckill/script/SeckillScriptExecutor.java`
- Create: `flash-seckill/src/test/java/com/flash/fulfill/seckill/script/SeckillScriptExecutorTest.java`

**Interfaces:**
- Produces: `SeckillScriptExecutor.execute(Long skuId, Long spuId, int quantity) -> int`
- Lua：KEYS[1]=库存key, KEYS[2]=sku状态key, KEYS[3]=spu状态key；ARGV[1]=qty, ARGV[2]=defaultStock；返回 0成功/2下架/3不足/4不存在

- [ ] **Step 1: `seckill_prededuct.lua`**（原子校验+扣减）
- [ ] **Step 2: `SeckillScriptExecutor.java`**
- [ ] **Step 3: 单测**
- [ ] **Step 4: 编译**
- [ ] **Step 5: 提交** `feat(seckill): Lua 原子校验+预扣脚本执行器`

---

### Task 3: 改写 SeckillService（去 Feign,接入 Lua + 分派 + rollback）

**Files:**
- Modify: `flash-seckill/src/main/java/com/flash/fulfill/seckill/service/SeckillService.java`
- Modify: `flash-seckill/src/test/java/com/flash/fulfill/seckill/service/SeckillServiceTest.java`
- Modify: `flash-seckill/pom.xml`（删除 openfeign + loadbalancer 依赖）
- Modify: `flash-seckill/src/main/java/com/flash/fulfill/seckill/SeckillApplication.java`（移除 `@EnableFeignClients`）
- Delete: `flash-seckill/src/main/java/com/flash/fulfill/seckill/feign/ProductClient.java`
- Delete: `flash-seckill/src/main/java/com/flash/fulfill/seckill/deductor/StockPreDeductor.java`
- Delete: `flash-seckill/src/main/java/com/flash/fulfill/seckill/deductor/RedisStockPreDeductor.java`
- Modify: `flash-common/src/main/java/com/flash/fulfill/common/dto/SeckillOrderCommand.java`（加 `Long spuId`）

**Interfaces:**
- Consumes: `SeckillScriptExecutor`、`SeckillResultCode`、`FlashOrderResponse(status)`
- Produces: `SeckillService.createFlashOrder` 依码分派；Mockito 单测各码路径

- [ ] **Step 1: `SeckillOrderCommand` 加 `Long spuId`**
- [ ] **Step 2: 改写 `SeckillService`**
- [ ] **Step 3: 单测**
- [ ] **Step 4: 清理 pom/Application/删除旧类**
- [ ] **Step 5: 编译**
- [ ] **Step 6: 提交** `refactor(seckill): 秒杀入口改为 Lua 原子校验+扣减`

---

### Task 4: flash-product 同步状态 key

**Files:**
- Create: `flash-product/src/main/java/com/flash/fulfill/product/cache/SeckillStatusWriter.java`
- Modify: `flash-product/src/main/java/com/flash/fulfill/product/service/SpuService.java`
- Modify: `flash-product/src/main/java/com/flash/fulfill/product/service/SkuService.java`
- Modify: 对应单测

**Interfaces:**
- Consumes: `StringRedisTemplate`
- Produces: `SeckillStatusWriter.spuStatus(Long spuId, boolean on)` / `skuStatus(Long skuId, boolean on)`
- 写入时机：与 `evictAfterCommit` 同时机（afterCommit）

- [ ] **Step 1: `SeckillStatusWriter.java`**
- [ ] **Step 2: SpuService 接入**
- [ ] **Step 3: SkuService 接入**
- [ ] **Step 4: 单测**
- [ ] **Step 5: 编译**
- [ ] **Step 6: 提交** `feat(product): 同步秒杀轻量状态 key`

---

### Task 5: 全量验证

**Files:**
- Modify: `README.md`

- [ ] **Step 1:** `mvn -q -DskipTests package`
- [ ] **Step 2:** `mvn -q test`
- [ ] **Step 3: 更新 README** 秒杀流程 + 状态码 + curl 演示命令（加 spuId）
- [ ] **Step 4: 提交** `docs: 秒杀 Lua 链路说明`

---

## 前置说明
- `SeckillOrderCommand` 需加 `Long spuId`；HTTP 请求体需携带 `spuId`，README curl 演示命令同步更新。
- flash-seckill 删除 openfeign 后 `@EnableFeignClients` 移除；order 侧计价不受影响。
