# FlashFulfill — 分布式高并发秒抢与智能订单履约平台

> 骨架版(Skeleton)。目标是**可编译、可运行、全链路打通**,重基础设施按 TODO 留待后续里程碑。

## 架构

```text
[用户请求]
   │
   ▼
┌────────────────────────────────────────────────────────┐
│ flash-gateway :8080  (路由 / 演示鉴权 / 令牌桶防刷 / Sentinel占位) │
└───────────────────────────┬────────────────────────────┘
                            │
     ┌──────────────────────┼──────────────────────┐
     ▼                      ▼                      ▼
┌─────────────────┐  ┌───────────────┐  ┌─────────────────────┐
│  flash-seckill  │  │  flash-order  │  │   flash-inventory   │
│  :8081 (Redis预扣)│  │  :8082 (订单中心) │  │   :8083 (乐观锁扣减)  │
└────────┬────────┘  └───────┬───────┘  └──────────┬──────────┘
         │                   │                     │
         └────► RocketMQ ◄────┘                     │
                 │  FLASH_ORDER_CREATE / ORDER_FULFILL
                 ▼
        ┌─────────────────────────┐
        │   flash-fulfillment     │
        │   :8084 (智能派单/履约)   │
        └─────────────────────────┘

  支撑服务:
  flash-user    :8085  (注册/登录/会话)
  flash-product :8086  (SPU/SKU 商品与价格/上下架状态, 供 order 计价与 seckill 校验)
```

## 端到端演示链路

```
curl 网关 ──► flash-seckill:校验 SPU/SKU 双上架(Feign→flash-product) + 预扣库存(Redis)
        ──► RocketMQ [FLASH_ORDER_CREATE]
        ──► flash-order:幂等建单(INITIAL) ──► Feign 取真实单价(flash-product)计算金额
        ──► Feign 调用 flash-inventory:原子条件扣减库存
        ──► 订单置 CREATED ──► RocketMQ [ORDER_FULFILL]
        ──► flash-fulfillment:落派单记录(仓库路由/运单号) ──► Feign 回调订单置 DISPATCHED
```

## 技术栈

Java 21 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · Spring Cloud Alibaba 2023.0.3.2
Nacos 2.3.2 · Redis 7 · RocketMQ 5.1.4 · MySQL 8.0 · Seata 2.0.0(占位)

## 模块

| 模块 | 端口 | 说明 |
| :-- | --: | :-- |
| flash-common | - | 统一返回 Result / 错误码 / 全局异常 / MQ 契约 / DTO / ID 生成 |
| flash-gateway | 8080 | 网关路由、演示鉴权(Bearer token)、内存令牌桶防刷、Sentinel 依赖占位 |
| flash-seckill | 8081 | 秒抢下单:Redis DECR 预扣(简化版) → 投递建单命令 |
| flash-order | 8082 | 消费建单 → 幂等去重 → 建单 → Feign 扣库存 → 状态机流转 → 投递履约事件 |
| flash-inventory | 8083 | 库存 SKU 管理、乐观锁条件扣减(单条 UPDATE 原子防超卖) |
| flash-fulfillment | 8084 | 消费履约事件 → 智能仓库路由(简化) → 落派单记录 → 回调订单置已发货 |
| flash-user | 8085 | 用户注册 / 登录(签发 JWT + Redis 会话) / 当前用户 |
| flash-product | 8086 | SPU/SKU 两级商品模型、真实单价与上下架状态(Redis 缓存,order 计价与 seckill 校验来源) |

## 快速开始

### 1. 启动中间件(Docker 环境)

```bash
docker compose up -d
# 等待 mysql/nacos/rocketmq 就绪(约 30~60s)
```

> 需提前安装 Docker。骨架阶段 Seata 默认不启动;接入 Seata 时执行
> `docker compose --profile seata up -d seata-server`

### 2. 编译与单测

```bash
mvn -q -DskipTests package   # 全量编译打包
mvn test                     # 单元测试(无需中间件)
```

### 3. 启动服务(依次或并行)

```bash
java -jar flash-gateway/target/flash-gateway-1.0.0-SNAPSHOT.jar
java -jar flash-seckill/target/flash-seckill-1.0.0-SNAPSHOT.jar
java -jar flash-order/target/flash-order-1.0.0-SNAPSHOT.jar
java -jar flash-inventory/target/flash-inventory-1.0.0-SNAPSHOT.jar
java -jar flash-fulfillment/target/flash-fulfillment-1.0.0-SNAPSHOT.jar
```

### 4. 跑通全链路(curl)

```bash
# ① 提交秒抢下单(经网关,带演示 token)
curl -i http://localhost:8080/api/seckill/flash-orders \
  -H "Authorization: Bearer demo-token-001" \
  -H "X-User-Id: 1001" \
  -H "Content-Type: application/json" \
  -d '{"userId":1001,"skuId":1001,"activityId":1,"quantity":1}'

# 返回 data.requestId,例如:
# {"code":200,"message":"成功","data":{"requestId":"xxxxxx","message":"下单请求已受理,正在异步创建订单"}}

# ② 轮询订单状态(注意替换为上面的 requestId)
curl "http://localhost:8080/api/order/flash-orders?requestId=xxxxxx" \
  -H "Authorization: Bearer demo-token-001"

# ③ 查看库存变化(预扣与扣减后)
curl http://localhost:8080/api/inventory/stocks/1001 \
  -H "Authorization: Bearer demo-token-001"

# ④ 查看履约派单记录
curl http://localhost:8080/api/fulfillment/dispatch/FF1234567890 \
  -H "Authorization: Bearer demo-token-001"

# ⑤ 演示限流防刷:连续快速发起超过 20 次/秒会返回 429
```

## 数据模型

| 库 | 表 | 说明 |
| :-- | :-- | :-- |
| flash_order_db | flash_order | 订单,request_id 唯一幂等、order_no 唯一 |
| flash_inventory_db | stock | 库存,sku_id 唯一,available 条件扣减 |
| flash_fulfillment_db | dispatch_record | 派单履约记录,order_no 唯一 |

> 各服务启动时通过 `schema.sql` 自动建表;库存演示数据由 flash-inventory 启动时预热(sku 1001/1002)。

## 体系现状与 TODO(生产增强)

| 设计目标(需求) | 骨架状态 | 生产路线 |
| :-- | :-- | :-- |
| 10W+ QPS 预扣 | Redis DECR 简化版 | Lua 原子脚本 + 预扣令牌 + 对账回滚 |
| 零超卖 | 乐观锁条件 UPDATE | 预占 + DB 最终一致性对账 |
| 不漏单 | 幂等键 + 唯一约束 | RocketMQ 事务消息 + 本地消息表 |
| 15 分钟超时关单 | 无 | RocketMQ 延迟消息/定时任务 |
| 分布式事务(履约链路) | 异步 MQ 最终一致 | Seata AT/TCC 接入(compose 已备好) |
| 分库分表 | 单库单表 | ShardingSphere 基因分片 |
| 配置动态推送 | 仅注册中心 | Nacos Config 接入 |
| 真实鉴权 | 演示 token | jjwt 签名 + Redis 黑名单 |
| 智能仓配 | 取模路由 | 距离/成本/运力多维调度 |

## 目录结构

```
├── pom.xml                  # 根 POM(父+聚合)
├── docker-compose.yml       # 中间件编排
├── deploy/                  # mysql 初始化 / rocketmq broker 配置
├── docs/superpowers/specs/  # 设计文档
├── flash-common/            # 公共模块
├── flash-gateway/
├── flash-seckill/
├── flash-order/
├── flash-inventory/
└── flash-fulfillment/
```

## 说明

本骨架在无 Docker 的机器上以「全量编译 + 单元测试」验证;端到端链路需在具备 Docker 的机器上按上述步骤运行。