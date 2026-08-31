# FlashFulfill 项目总结

## 一、项目概览

**定位**:分布式高并发秒抢与智能订单履约平台。围绕"秒杀下单 → 订单异步创建 → 支付 → 扣库存 → 智能派单履约"构建完整闭环,以**异步化、事务性消息、全链路幂等**为设计主线。

**技术栈**:Java 21 / Spring Boot 3.3.5 / Spring Cloud Alibaba(Nacos 注册发现)/ Spring Cloud Gateway / RocketMQ / Redis(含 Stream)/ MySQL / MyBatis-Plus / Sentinel / OpenFeign / JWT

**服务矩阵**(10 个模块):

| 服务 | 端口 | 职责 |
|------|------|------|
| flash-common | - | 共享常量 / DTO / 工具(无独立进程) |
| flash-gateway | 8080 | 网关:JWT 鉴权、会话校验、Sentinel 限流、路由 |
| flash-seckill | 8081 | 秒杀入口:Lua 原子预扣 + 写建单事件 |
| flash-order | 8082 | 订单:消息建单(Outbox)、状态机、确认收货 |
| flash-inventory | 8083 | 库存:MQ 扣减命令消费、流水表幂等 |
| flash-fulfillment | 8084 | 履约:消费履约事件、智能派单、回调发货 |
| flash-user | 8085 | 用户注册 / 登录(JWT 签发) |
| flash-product | 8086 | 商品 SKU/SPU 管理、秒杀状态/价格 key 维护 |
| flash-relay | 8087 | 事件转发:Redis Stream → RocketMQ |
| flash-payment | 8088 | 支付:支付单、模拟网关回调、Outbox 可靠回调订单 |

**中间件**:MySQL 6 库 11 表、Redis(秒杀库存 / 幂等 / 价格 / 会话 / 事件 Stream)、RocketMQ 3 个主题、Nacos 注册中心。

---

## 二、核心设计

### 1. 秒杀原子链路 —— Lua 单次 EVAL

秒杀请求在一次 Redis 往返(`seckill_prededuct.lua`)内原子完成:

- **幂等占位**:`SET reqKey PROCESSING NX` + 多态记忆(`SUCCESS/LIMIT/OFF_SHELF/STOCK/PRICE`),并发重复请求返回既有状态;
- **SKU/SPU 状态校验**:读取商品侧维护的状态 key,下架/不存在即拒绝;
- **限购计数**:`INCR buyKey` 超限回退;
- **价格校验**:扣库存前从 `seckill:price:{skuId}` 读取价格,缺失/非法直接拒绝(新增错误码 7,fail-fast);
- **库存预扣**:`DECRBY stockKey`(库存由库存服务启动时从 DB 预热);
- **写建单事件**:`XADD` 到 Redis Stream(多 field-value,含价格)。

**亮点**:"扣减 Redis 库存"与"写建单事件"在同一次 EVAL 内完成,消除两者之间的一致性窗口,杜绝"库存已扣但消息未发"。

### 2. Redis Stream 多字段事件 + 独立 relay 转发

- 建单事件以**多 field-value** 形式写入 Stream(`requestId/userId/skuId/spuId/activityId/quantity/price`),**不拼大 JSON**,更安全、可读、便于逐字段校验;
- `flash-relay` 用 `StreamMessageListenerContainer`(消费组 `flash-seckill-order-relay`)监听,**手动 ACK**:转发 RocketMQ 成功才 `XACK`,失败留在 pending 由容器重读重发;字段缺失/非法直接 ACK 跳过避免卡队列;
- relay 职责单一,只做 Stream → MQ 转发(订单侧 Outbox 已回归订单服务自维护)。

### 3. 事务性 Outbox(本地消息表)可靠投递

将"业务操作"与"消息发送"放在同一本地事务,消除分布式不一致:

| 服务 | Outbox 表 | 消息类型 | 转发目标 |
|------|-----------|----------|----------|
| flash-order | order_outbox | DEDUCT(扣库存命令)/ FULFILL(履约事件) | RocketMQ |
| flash-payment | payment_outbox | MARK_PAID(支付成功回调) | Feign 订单 markPaid |

- Outbox 与业务同事务写入(订单建单、支付回调成功),提交即保证消息不丢;
- 轮询转发用 `FOR UPDATE SKIP LOCKED` 抢取,**多实例部署同一条消息仅被一个实例处理**;发送成功置 `SENT`,失败 `retry_count+1` 保留 `PENDING` 下轮重试;
- 订单侧由 `OrderOutboxRelay`(@Scheduled)自维护,支付侧由 `PaymentOutboxRelay` 自维护。

### 4. 全链路幂等(不重不丢)

| 环节 | 幂等键 / 机制 |
|------|---------------|
| 订单建单 | `flash_order.uk_request_id` 唯一键,重复 MQ 消息触发 DuplicateKeyException,消费端捕获忽略(不预查询,直接插) |
| 库存扣减 | `stock_flow.uk_request_id` 唯一键 + 幂等判断,重复扣减命令直接返回 |
| 支付单 | `payment_record.uk_order_no` / `uk_pay_no`,重复发起/重复回调幂等 |
| 履约派单 | `dispatch_record.uk_order_no`,重复履约事件忽略 |
| 订单状态流转 | 各状态方法带**状态守卫**(仅合法状态迁移),重复回调天然幂等 |

### 5. 订单状态机 + 超时任务

```
INITIAL →(确认订单填地址)→ PENDING_PAYMENT →(支付成功)→ PENDING_SHIPMENT
        →(履约发货)→ SHIPPED →(确认收货/超时自动)→ COMPLETED
任意合适阶段 → CANCELLED
```

- **异步建单落地 INITIAL**:客户端用 `requestId` 轮询,订单行未落库返回 INITIAL 软状态;
- **确认订单**:详情页填地址后提交,同步事务更新订单(INITIAL→PENDING_PAYMENT)并 upsert 收货地址表;
- **支付成功**:订单 → PENDING_SHIPMENT 并写 FULFILL 履约事件;
- **超时任务**(`OrderTimeoutCanceler`):15 分钟未支付自动取消;已发货 7 天未确认收货自动完成。

### 6. 全异步解耦

- **秒杀请求线程**:只执行 Lua 预扣即返回"已受理",不阻塞等待 MQ;
- **建单**:MQ 消息 → 订单服务插订单 + Outbox,库存扣减经 Outbox → MQ → 库存服务(扣减**不回写订单状态**,纯命令驱动);
- **支付**:支付单(INITIAL)→ 模拟网关异步回调(PAID)→ Outbox 重推 markPaid;
- **履约**:FULFILL 事件 → 履约服务派单 → 回调订单置发货。

### 7. 价格链路

- 商品侧维护 `seckill:price:{skuId}`:SKU 生命周期(建/改/上下架)写 24h TTL;查询侧 `SkuPriceService` 用**分布式锁重建**防止缓存击穿(持锁查 DB 回填,未抢到锁等待重读);
- 秒杀 Lua 读取价格并校验 → 随 Stream 多字段透传 → 订单服务**以消息内价格计价**(不再自取价格)。

### 8. 网关鉴权 + 限流

- `JwtAuthFilter`:JWT(HS256)验签 → Redis 会话比对 → 透传 `X-User-Id`;登录/注册白名单;
- Sentinel 网关限流:全局 API / 可信 IP / 登录用户三个维度,基线规则 + Nacos 动态下发。

### 9. 库存扣减

- 乐观锁条件更新(`UPDATE ... WHERE available >= qty`),单条 SQL 天然原子防超卖;
- 扣减与流水表同事务,`stock_flow` 幂等防重复消费;库存启动时从 DB 预热 Redis 秒杀库存 key。

---

## 三、亮点设计(速览)

1. **Lua 原子性**:一次往返完成幂等占位 + 限购 + 价格校验 + 预扣 + 事件写入,天然防超卖、防重复;
2. **事务性 Outbox**:订单/支付各一张本地消息表,业务与消息同事务,配合 `FOR UPDATE SKIP LOCKED` 轮询,保证"不丢不重"且支持多实例水平扩展;
3. **幂等体系贯穿全链路**:订单/库存/支付/履约四处唯一键 + 状态守卫,消息重复消费无害;
4. **多字段 Stream 事件替代 JSON**:逐字段构建命令,字段缺失即拒,规避大 JSON 序列化/解析风险;
5. **独立 relay 服务**:职责单一,手动 ACK 精确控制投递语义;
6. **异步解耦**:秒杀线程零等待、扣库存/支付/履约全异步,天然削峰;
7. **状态守卫的领域模型**:每个状态迁移方法校验前置状态,重复回调天然幂等,超时任务兜底闭环。

---

## 四、数据与中间件一览

**数据库**(6 库 11 表):

| 数据库 | 表 |
|--------|----|
| flash_order_db | flash_order / order_outbox / order_addresses |
| flash_inventory_db | stock / stock_flow |
| flash_fulfillment_db | dispatch_record |
| flash_product_db | category / brand / spu / sku |
| flash_payment_db | payment_record / payment_outbox |
| flash_user_db | flash_user |

**Redis Key**:

| Key | 用途 |
|-----|------|
| `user:session:{tokenHash}` | 登录会话 |
| `seckill:req:{requestId}` | 秒杀请求幂等状态 |
| `seckill:stock:{skuId}` | 秒杀库存(预扣) |
| `seckill:sku:status:{skuId}` / `seckill:spu:status:{spuId}` | 上下架状态 |
| `seckill:price:{skuId}` | 秒杀价格 |
| `seckill:price:lock:{skuId}` | 价格重建锁(防击穿) |
| `seckill:buy:{userId}:{activityId}:{skuId}` | 限购计数 |
| `seckill:event:order` | 建单事件 Stream |
| `product:sku:{skuId}` | 商品出售视图缓存 |

**RocketMQ 主题**:`FLASH_ORDER_CREATE`(建单)、`INVENTORY_DEDUCT`(扣库存命令)、`ORDER_FULFILL`(履约事件)。

---

## 五、已知缺口 / 生产化 TODO

- **超时关单不归还库存**:MySQL 扣减在建单后异步执行,超时取消未回滚(需对账补偿);
- **订单取消后支付无退款**:支付单已 PAID 但订单已取消,无退款/冲正流程;
- **分布式事务**:履约"派单 + 回调订单"未做强一致,可接入 Seata AT/TCC;
- **精确超时**:定时扫描改为 RocketMQ 延迟消息精确触发;
- **分库分表**:订单/库存可 ShardingSphere 按 SKU 基因分片;
- **锁释放**:价格重建锁改为 Lua compare-and-delete(当前直接删除,毫秒级可接受);
- **真实支付**:当前为模拟网关回调,需对接真实支付渠道。