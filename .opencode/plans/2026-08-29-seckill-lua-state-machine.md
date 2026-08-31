# 秒杀入口 Lua 幂等状态机 + 限购 改造 — 实施计划

**Goal:** 用「一次 Lua EVAL」实现秒杀入口的 requestId 幂等状态机（PROCESSING/SUCCESS/LIMIT/OFF_SHELF）+ 限购校验 + 库存原子扣减；事件仍经 RocketMQ 投递，仅库存不足允许重试。

**Architecture:** 秒杀侧不再走 Feign。Lua 脚本以 `seckill:req:{requestId}` 作幂等缓存（四态）、`seckill:buy:{userId}:{activityId}:{skuId}` 作限购计数（上限 1）、`seckill:stock:{skuId}` 作库存。requestId 为 null 在网关层拒绝。

**Tech Stack:** Java 21 / Spring Boot 3.3.5 / Redis(DefaultRedisScript) / RocketMQ。无新增依赖。

**Date:** 2026-08-29

## Global Constraints

- Java 21；Spring Boot 3.3.5；Spring Cloud Alibaba 2023.0.3.2
- 对外接口：HTTP 恒 200；顶层 `Result.code` 不变；秒杀业务结果在 `FlashOrderResponse.status`
- 状态码：`0=成功` `1=参数非法` `2=商品下架` `3=库存不足` `4=商品或库存不存在` `5=重复/处理中`
- Redis keys：`seckill:req:{requestId}`、`seckill:buy:{userId}:{activityId}:{skuId}`、`seckill:stock:{skuId}`
- 幂等：requestId 占位失败 → 读缓存状态返回；首次成功仅库存不足删除占位可重试；OFF_SHELF/LIMIT/SUCCESS 缓存不重试
- 限购阈值 1 件；限购+1 与判断原子（Lua 内）
- requestId 为 null/blank 由网关层校验拒绝
- 事件走 RocketMQ `FLASH_ORDER_CREATE`；不做 Stream 化、不做下单失败补偿
- 秒杀侧不再使用 ProductClient；删除旧预扣 deductor
- 仅库存不足删除 requestId 占位

---

### Task 1: 状态码补充
**Files:**
- Modify: `flash-common/.../constant/SeckillResultCode.java`（补 `REPEATED=5` 等）

- [ ] **Step 1:** 补常量 `PROCESSING/REPEATED`
- [ ] **Step 2:** `mvn -q -pl flash-common -am -DskipTests package`
- [ ] **Step 3: 提交**

---

### Task 2: 网关层 requestId 非空校验
**Files:**
- Modify: `flash-gateway/.../filter/JwtAuthFilter.java`
- Test: `flash-gateway/.../filter/JwtAuthFilterTest.java`

- [ ] **Step 1:** 认证放行后，对非公开 POST /api/seckill/** 校验 body 中 requestId 非空？→ 改为：在网关对 `/api/seckill/**` 报文解析 requestId，null/blank 返回 400。
- [ ] **Step 2: 单测**
- [ ] **Step 3: 编译**
- [ ] **Step 4: 提交**

---

### Task 3: 重写 Lua 幂等状态机
**Files:**
- Modify: `flash-seckill/src/main/resources/lua/seckill_prededuct.lua`

逻辑：
```
SETNX req "PROCESSING"
if false → GET req → 返回(SUCCESS/PROCESSING/LIMIT/OFF_SHELF)
读 spu/sku 状态 → 任一非1/缺失 → SET req=OFF_SHELF → return 2
lim = INCR buy
if lim > 1 → DECR buy; SET req=LIMIT → return 限购码(6?)
stock 不存在 → 初始化 default
if stock < qty → DEL req(可重试) → return 3
DECR stock
SET req=SUCCESS TTL
return 0
```

- [ ] **Step 1: 重写 lua**
- [ ] **Step 2: 编译**
- [ ] **Step 3: 提交**

---

### Task 4: SeckillScriptExecutor 支持限购与状态
**Files:**
- Modify: `flash-seckill/.../script/SeckillScriptExecutor.java`
- Test: `flash-seckill/.../script/SeckillScriptExecutorTest.java`

- [ ] **Step 1:** execute 签名扩展 `(Long skuId, Long spuId, Long userId, Long activityId, int quantity)`
- [ ] **Step 2: 单测**
- [ ] **Step 3: 编译**
- [ ] **Step 4: 提交**

---

### Task 5: 改写 SeckillService 分派
**Files:**
- Modify: `flash-seckill/.../service/SeckillService.java`
- Test: `.../SeckillServiceTest.java`

- [ ] **Step 1:** 接入新 execute；SUCCESS→发 MQ；其余按码返回 FlashOrderResponse(status)
- [ ] **Step 2: 单测**
- [ ] **Step 3: 编译**
- [ ] **Step 4: 提交**

---

### Task 6: 全量验证 + README
**Files:**
- Modify: `README.md`

- [ ] **Step 1:** `mvn -q -DskipTests package`
- [ ] **Step 2: 更新 README**（限购、幂等状态码、requestId 必填、演示命令）
- [ ] **Step 3: 提交**
