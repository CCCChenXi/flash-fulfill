# FlashFulfill 可构建骨架 — 设计文档

日期:2026-08-21
状态:已确认(评审后进入实施)
实施深度:可构建骨架(Buildable Skeleton)

## 1. 目标

产出可直接 `mvn package` 编译、可运行的最小版 FlashFulfill 微服务中台骨架,
证明“网关 → 秒抢 → 订单 → 库存 → 履约”五服务链路真实打通。
重基础(状态机/Lua/Seata/ShardingSphere)按 TODO 占位,留待后续里程碑。

## 2. 关键决策(经用户确认)

- 单仓库 Maven 多模块 Reactor + 公共模块 `flash-common`(方案 A)
- 构建/验证:Java 21 + Maven 3.9.9;Docker 本机不可用 → 全量编译 + 单元测试为验证手段,
  docker-compose 与运行手册面向具备 Docker 的机器
- 端到端演示链路:Seckill → (RocketMQ) → Order → (Feign) → Inventory → (RocketMQ) → Fulfillment → (Feign) → Order 置已发货
- 中文注释 / 中文 README

## 3. 版本矩阵

| 组件 | 版本 |
| :-- | :-- |
| Java | 21 |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.3(Leyton) |
| Spring Cloud Alibaba | 2023.0.3.2 |
| RocketMQ Spring Boot Starter | 2.3.2(需 `@Import(RocketMQAutoConfiguration)`) |
| 中间件(compose) | Nacos 2.3.2 / MySQL 8.0 / Redis 7 / RocketMQ 5.1.4 / Seata 2.0.0(占位) |

## 4. 模块与端口

| 模块 | 端口 | 职责 | 关键依赖 |
| :-- | --: | :-- | :-- |
| flash-gateway | 8080 | 路由 / JWT(演示token) / 内存令牌桶限流 | gateway, nacos-discovery, sentinel, loadbalancer |
| flash-seckill | 8081 | 秒抢下单:Redis 预扣 → 发 `FLASH_ORDER_CREATE` | redis, rocketmq, sentinel |
| flash-order | 8082 | 消费建单 → Feign 扣库存 → 发 `ORDER_FULFILL` | jpa, openfeign, rocketmq, mysql |
| flash-inventory | 8083 | 库存乐观锁扣减 / 查询 | jpa, mysql |
| flash-fulfillment | 8084 | 消费履约 → 简化派单 → 回调订单置已发货 | jpa, openfeign, rocketmq, mysql |

## 5. 端到端链路

```
客户端 → gateway:8080/api/seckill/flash-orders (Bearer demo-token-001)
  → flash-seckill:Redis 预扣(seckill:stock:{skuId}) → RocketMQ [FLASH_ORDER_CREATE]
  → flash-order:消费者创建订单(INITIAL) → Feign POST flash-inventory /api/inventory/internal/deduct
  → flash-inventory:UPDATE stock SET available=available-? WHERE skuId=? AND available>=?（乐观扣减）
  → flash-order:订单置 CREATED → RocketMQ [ORDER_FULFILL]
  → flash-fulfillment:写入 dispatch_record → Feign PUT flash-order /internal/orders/{orderNo}/dispatched
  → flash-order:订单置 DISPATCHED（全链路闭环）
```

查询:客户端轮询 `gateway:8080/api/order/flash-orders?requestId=xxx` 查看订单/发货状态。

## 6. 消息契约(flash-common)

- Topic `FLASH_ORDER_CREATE` / Tag `ORDER_CREATE`,payload `SeckillOrderCommand`
- Topic `ORDER_FULFILL` / Tag `FULFILL`,payload `OrderFulfillEvent`
- 消费者组:`flash-order-create-consumer` / `flash-order-fulfill-consumer`

## 7. 数据库(每服务一库,启动时 schema.sql 建表)

- flash_order_db.flash_order(request_id 幂等唯一键、status 状态机 INITIAL→CREATED→DISPATCHED/FAILED/CLOSED)
- flash_inventory_db.stock(sku_id 唯一、available 乐观扣减)
- flash_fulfillment_db.dispatch_record(order_no 幂等唯一键)

## 8. TODO 占位(生产增强项,骨架不实现)

- 秒抢 Lua 原子预扣(现为 Redis DECR 简化版)
- Seata 分布式事务接入(履约链路强一致)
- ShardingSphere 分库分表配置
- Nacos 配置中心动态推送(现仅使用注册中心)
- RocketMQ 事务消息 / 15 分钟延迟关单(DelayQueue)
- 真实 JWT 签发与校验(jjwt)+ Redis 黑名单
- Sentinel 真实规则与 Dashboard 落库(现仅 QPS 规则示例)
- 智能仓配路由(现为占位取模路由)

## 9. 验证

- `mvn -q -DskipTests package`(全模块编译、打可执行 jar)
- `mvn -q test`(各服务服务层单元测试,依赖 Mockito,不需要中间件)
- 端到端:具备 Docker 的机器 `docker compose up -d` + README curl 演示脚本