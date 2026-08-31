# 秒杀事件 Redis Stream → RocketMQ 转发（独立 relay 服务）— 实施计划

**Goal:** 消除"扣减 Redis 库存"与"发送 RocketMQ"之间的不一致窗口：Lua 原子完成扣库存 + 写入 Redis Stream 事件，由独立服务 flash-relay 消费 Stream 转发到 RocketMQ。

**Architecture:**
- flash-seckill 请求线程：Lua 原子扣减库存 + `XADD` 事件到 `seckill:event:order` → 立即返回"已受理"（不再同步发 MQ、不再回滚）。
- 新增独立服务 flash-relay：用 Spring Data Redis `StreamMessageListenerContainer`（后台线程，实现伪代码 `while(true){read→send→ack}`）监听 Stream → `rocketMQTemplate.syncSend(FLASH_ORDER_CREATE)` → 成功 `XACK`，失败不 ACK 待重读重发。
- flash-order 不变：继续 `@RocketMQMessageListener` 消费 `FLASH_ORDER_CREATE`（已有 `existsByRequestId` 幂等，重复投递无害）。

**Tech Stack:** Java 21 / Spring Boot 3.3.5 / Spring Data Redis Stream API / RocketMQ。无新增第三方依赖。

**Date:** 2026-08-30

## Global Constraints

- Lua 扣减成功后 XADD 事件到 Stream，再 SET reqKey=SUCCESS；事件内容 = SeckillOrderCommand 的 JSON
- Stream key `seckill:event:order`；消费组 `flash-seckill-order-relay`
- flash-relay 为独立可运行模块（新 pom + Application + 配置）
- relay 用 `StreamMessageListenerContainer`（异步容器），手动 ACK：send 成功才 ack，失败不 ack
- 请求线程不再 `syncSend`、不再 `rollbackIfNeeded`（扣减成功即受理）
- `RedisKeys` 增 Stream key/group 常量
- flash-relay 需依赖 flash-common + data-redis + rocketmq
- 全量编译 `mvn -q -DskipTests package` 通过

---

### Task 1: RedisKeys 增常量 + Lua XADD
**Files:**
- Modify: `flash-common/.../constant/RedisKeys.java`
- Modify: `flash-seckill/src/main/resources/lua/seckill_prededuct.lua`

- [ ] **Step 1:** RedisKeys 增 `SECKILL_EVENT_STREAM = "seckill:event:order"`、`SECKILL_EVENT_GROUP = "flash-seckill-order-relay"`
- [ ] **Step 2:** Lua：KEYS 增 Stream key；扣减成功后 `XADD streamKey * eventJson`（ARGV 增 event JSON）

### Task 2: SeckillScriptExecutor 传 Stream key + event
**Files:**
- Modify: `flash-seckill/.../script/SeckillScriptExecutor.java`

- [ ] **Step 1:** `execute` 增 Stream key 到 keys；ARGV 增 `eventJson`（`ObjectMapper.writeValueAsString(cmd)`）
- [ ] **Step 2:** 移除 `rollback` 方法（不再需要）

### Task 3: SeckillService 去 syncSend
**Files:**
- Modify: `flash-seckill/.../service/SeckillService.java`

- [ ] **Step 1:** 移除 `rocketMQTemplate` 注入与 `submitOrder` 的 syncSend；扣减成功后直接返回"已受理"
- [ ] **Step 2:** 移除 `rollbackIfNeeded`；`RocketMQTemplate` 不再注入

### Task 4: 新增 flash-relay 模块
**Files:**
- Create: `flash-relay/pom.xml`
- Create: `flash-relay/src/main/java/com/flash/fulfill/relay/RelayApplication.java`
- Create: `flash-relay/src/main/resources/application.yml`
- Create: `flash-relay/.../relay/OrderEventRelay.java`（StreamMessageListenerContainer 配置 + onMessage → send → ack）
- Create: `flash-relay/.../relay/OrderEventStreamListener.java`（手动 ACK 的 StreamListener）
- Modify: `pom.xml`（root modules 加 flash-relay）

- [ ] **Step 1:** pom（parent + web/nacos-discovery/data-redis/rocketmq/flash-common/lombok）
- [ ] **Step 2:** RelayApplication（`@SpringBootApplication @EnableDiscoveryClient @Import(RocketMQAutoConfiguration.class)`）
- [ ] **Step 3:** application.yml（port 8087、redis、rocketmq、stream group/consumer）
- [ ] **Step 4:** OrderEventRelay 配置 `StreamMessageListenerContainer`（消费组、手动 ACK、错误处理）
- [ ] **Step 5:** OrderEventStreamListener.onMessage → 解析 SeckillOrderCommand → syncSend(FLASH_ORDER_CREATE) → ack / 抛异常不 ack
- [ ] **Step 6:** root pom modules 加 flash-relay

### Task 5: 测试与全量编译
**Files:**
- Modify: 相关单测（SeckillScriptExecutorTest、SeckillServiceTest）
- Create: `flash-relay/.../relay/OrderEventStreamListenerTest.java`（可选）

- [ ] **Step 1:** 调整 SeckillScriptExecutorTest（execute 参数变化）
- [ ] **Step 2:** 调整 SeckillServiceTest（移除 rocketMQTemplate mock）
- [ ] **Step 3:** `mvn -q -DskipTests package`