# flash-product 商品服务 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增商品服务（SPU/SKU 两级模型），并把其**真实单价**与**双状态**接入 order 计价与 seckill 校验链路。

**Architecture:** 独立微服务 flash-product（:8086），含 category/brand/spu/sku 四表；category/brand 只建表无代码；spu 一对多 sku。SKU 状态读写走 Redis 缓存；order/seckill 通过 Feign 访问 `GET /api/product/sku/{skuId}` 获取 `SkuSellView`（含 sku 与 spu 双状态、价格）。库存侧去 `sku_name`，只保留 skuId 与数量。

**Tech Stack:** Java 21 / Spring Boot 3.3.5 / Spring Cloud Alibaba 2023.0.3.2 / MyBatis-Plus 3.5.7 / MySQL 8 / Redis 7 / Feign / Mockito。

**Date:** 2026-08-27

## Global Constraints

- Java 21；Spring Boot 3.3.5；Spring Cloud Alibaba 2023.0.3.2；MyBatis-Plus 3.5.7
- 统一返回 `com.flash.fulfill.common.api.Result`；业务异常 `com.flash.fulfill.common.exception.BizException(ErrorCode)`
- DDL 严格按四表原文建表（`charset=utf8mb4`、`uk_sku_code`、`fk_sku_spu`）；`category`/`brand` 仅建表，不写任何 Java 代码
- SKU 业务主键用 `sku.id`；`sku_code` 仅作展示/编码，不参与业务链路
- 删除一律逻辑下架 `status=0`（TINYINT：0 下架 / 1 上架）
- 根 POM modules 追加 `flash-product`；`deploy/mysql/init.sql` 追加 `flash_product_db`
- 每个 Java 命令行段均须 `mvn` 可编译；单测用 Mockito，无需中间件

---

### Task 1: flash-product 模块骨架

**Files:**
- Create: `flash-product/pom.xml`
- Create: `flash-product/src/main/resources/application.yml`
- Create: `flash-product/src/main/resources/schema.sql`（四表 DDL）
- Create: `flash-product/src/main/java/com/flash/fulfill/product/ProductApplication.java`
- Modify: `pom.xml:22-30`（modules 追加 flash-product）
- Modify: `deploy/mysql/init.sql:1-3`（追加 `CREATE DATABASE flash_product_db ...`）

**Interfaces:**
- Produces: 模块可编译；`ProductApplication` 入口含 `@SpringBootApplication @EnableDiscoveryClient @MapperScan("com.flash.fulfill.product.mapper")`

- [ ] **Step 1: 写 `flash-product/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flash.fulfill</groupId>
        <artifactId>flash-fulfill</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>flash-product</artifactId>
    <name>flash-product</name>
    <description>商品服务:SPU / SKU 两级商品模型、状态与单价</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.flash.fulfill</groupId>
            <artifactId>flash-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.flash.fulfill.product.ProductApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 写 `flash-product/src/main/resources/application.yml`**

```yaml
server:
  port: 8086

spring:
  application:
    name: flash-product
  threads:
    virtual:
      enabled: true
  cloud:
    nacos:
      discovery:
        server-addr: 172.25.80.175:8848
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/flash_product_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: cx348452
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always
  data:
    redis:
      host: 172.25.80.175
      port: 6379

mybatis-plus:
  mapper-locations: classpath*:mapper/*.xml
  type-aliases-package: com.flash.fulfill.product.entity
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    banner: false

product:
  cache:
    secs: 600
  seed:
    enabled: true

logging:
  level:
    com.flash.fulfill: INFO
    com.flash.fulfill.product.mapper: debug
```

- [ ] **Step 3: lash-product/src/main/resources/schema.sql** Four table DDL verbatim (with uk_sku_code and k_sku_spu):

[snip DDL written to node]

CREATE TABLE IF NOT EXISTS category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'CATEGORY ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT 'parent category id: 0 means top-level',
    name VARCHAR(100) NOT NULL COMMENT 'category name',
    sort INT NOT NULL DEFAULT 0 COMMENT 'sort order',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'status: 0 disabled, 1 enabled',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product category table';

CREATE TABLE IF NOT EXISTS brand (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'BRAND ID',
    name VARCHAR(100) NOT NULL COMMENT 'brand name',
    logo VARCHAR(500) DEFAULT NULL COMMENT 'brand logo',
    description VARCHAR(500) DEFAULT NULL COMMENT 'brand description',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'status: 0 disabled, 1 enabled',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product brand table';

CREATE TABLE IF NOT EXISTS spu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SPU ID',
    name VARCHAR(200) NOT NULL COMMENT 'product name',
    category_id BIGINT NOT NULL COMMENT 'category id',
    brand_id BIGINT DEFAULT NULL COMMENT 'brand id',
    description TEXT COMMENT 'product description',
    main_image VARCHAR(500) DEFAULT NULL COMMENT 'main image',
    status TINYINT NOT NULL DEFAULT 0 COMMENT 'status: 0 off shelf, 1 on shelf',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_brand_id (brand_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product SPU table';

CREATE TABLE IF NOT EXISTS sku (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    spu_id BIGINT NOT NULL COMMENT 'SPU ID',
    sku_code VARCHAR(64) NOT NULL COMMENT 'SKU code',
    name VARCHAR(200) NOT NULL COMMENT 'SKU name',
    price DECIMAL(12,2) NOT NULL COMMENT 'sale price',
    image VARCHAR(500) DEFAULT NULL COMMENT 'SKU image',
    specs JSON DEFAULT NULL COMMENT 'spec info',
    status TINYINT NOT NULL DEFAULT 0 COMMENT 'status: 0 off shelf, 1 on shelf',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    KEY idx_spu_id (spu_id),
    KEY idx_status (status),
    CONSTRAINT fk_sku_spu FOREIGN KEY (spu_id) REFERENCES spu(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product SKU table';



- [ ] **Step 4: 写 `ProductApplication.java`**（`@SpringBootApplication @EnableDiscoveryClient @MapperScan("com.flash.fulfill.product.mapper")`）

- [ ] **Step 5: 根 `pom.xml` modules 追加 `<module>flash-product</module>`；`deploy/mysql/init.sql` 追加 `flash_product_db`**

- [ ] **Step 6: 验证** `mvn -q -DskipTests package`
- [ ] **Step 7: 提交** `git commit -m "feat(product): 商品服务骨架"`

---

### Task 2: Spu CRUD

**Files:**
- Create: `flash-product/src/main/java/com/flash/fulfill/product/entity/Spu.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/mapper/SpuMapper.java`
- Create: `flash-product/src/main/resources/mapper/SpuMapper.xml`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/dto/SpuCreateCommand.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/dto/SpuUpdateCommand.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/dto/SpuView.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/service/SpuService.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/controller/SpuController.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/web/GlobalExceptionHandler.java`
- Create: `flash-product/src/test/java/com/flash/fulfill/product/service/SpuServiceTest.java`
- Modify: `flash-common/src/main/java/com/flash/fulfill/common/api/ErrorCode.java`（追加 `PRODUCT_NOT_FOUND(10010,"商品不存在")`、`PRODUCT_OFF_SHELF(10011,"商品已下架")`）

**Interfaces:**
- Produces: `SpuService.create(SpuCreateCommand)`, `SpuService.update(Long id, SpuUpdateCommand)`, `SpuService.onShelf(Long id)`, `SpuService.offShelf(Long id)`, `SpuService.get(Long id) → SpuView`
- Consumes: `ErrorCode.PRODUCT_NOT_FOUND`
- `SpuView`: id, name, categoryId, brandId, description, mainImage, status

- [ ] **Step 1: 失败测试**（TDD，mock SpuMapper）`createAndSkusView` + `updateSetsFields`，先 FAIL
- [ ] **Step 2: 实现** entity / mapper + XML / DTO / service / controller
- [ ] **Step 3: 运行测试** PASS
- [ ] **Step 4: 提交** `git commit -m "feat(product): SPU 增删改查"`

---

### Task 3: Sku CRUD

**Files:**
- Create: `flash-product/src/main/java/com/flash/fulfill/product/entity/Sku.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/mapper/SkuMapper.java`
- Create: `flash-product/src/main/resources/mapper/SkuMapper.xml`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/dto/SkuCreateCommand.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/dto/SkuUpdateCommand.java`
- Create: `flash-common/src/main/java/com/flash/fulfill/common/dto/SkuSellView.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/service/SkuService.java`
- Create: `flash-product/src/main/java/com/flash/fulfill/product/controller/SkuController.java`
- Create: `flash-product/src/test/java/com/flash/fulfill/product/service/SkuServiceTest.java`

**Interfaces:**
- Consumes: `SkuSellView`（flash-common）、`ErrorCode`
- Produces: `SkuController.GET /api/product/sku/{skuId}` → `Result<SkuSellView>`（order/seckill 共用）；`POST /api/product/sku`、`PUT /api/product/sku/{id}`、`PUT /api/product/sku/{id}/status`
- `SkuService.create(SkuCreateCommand)`, `SkuService.update(Long id, SkuUpdateCommand)`, `SkuService.setStatus(Long id, int status)`, `SkuService.getSellView(Long skuId) → SkuSellView`
- `SkuSellView` 字段: `skuId (Long), spuId (Long), skuName (String), price (BigDecimal), skuStatus (Integer), spuStatus (Integer)`
- 校验: `sku_code` 唯一（`uk_sku_code` + 前置查重）；`skusellview` 需读取其父 SPU 的 status

- [ ] **Step 1: 失败测试** `getSellViewMergesParentStatus`（mock SkuMapper + SpuMapper）→ FAIL
- [ ] **Step 2: 实现** entity / mapper / XML / DTO / service / controller
- [ ] **Step 3: 运行测试** PASS
- [ ] **Step 4: 提交** `git commit -m "feat(product): SKU 增删改查与出售视图"`

---

### Task 4: Redis 状态缓存

**Files:**
- Create: `flash-product/src/main/java/com/flash/fulfill/product/cache/SkuCacheService.java`
- Create: `flash-product/src/test/java/com/flash/fulfill/product/cache/SkuCacheServiceTest.java`
- Modify: `flash-product/src/main/java/com/flash/fulfill/product/service/SkuService.java`（getSellView 改走缓存）
- Modify: `flash-product/src/main/java/com/flash/fulfill/product/service/SpuService.java`（上下架/改名后失效相关缓存）

**Interfaces:**
- Consumes: `SkuService` 的 DB 回源逻辑；`StringRedisTemplate`
- Produces: `SkuCacheService.getSellView(Long skuId) → SkuSellView`（key=`product:sku:{skuId}`，TTL=`product.cache.secs`(600)，miss→DB→回填）；`SkuCacheService.evictSku(Long skuId)`；`SkuCacheService.evictSpu(Long spuId)`（删该 spu 下所有 sku 缓存）
- JSON 序列化用 `ObjectMapper`

- [ ] **Step 1: 失败测试** 命中缓存、未命中回源、evict 后回源 → FAIL
- [ ] **Step 2: 实现** SkuCacheService + 集入 SkuService/SpuService
- [ ] **Step 3: 运行测试** PASS
- [ ] **Step 4: 提交** `git commit -m "feat(product): SKU 出售视图 Redis 缓存"`

---

### Task 5: 种子数据预热

**Files:**
- Create: `flash-product/src/main/java/com/flash/fulfill/product/config/ProductDataInitializer.java`

**Interfaces:**
- Consumes: SpuMapper、SkuMapper、（可选 CategoryMapper 不建）— 直接插入
- Produces: 预热 spu(id 1,2)、sku(id **1001**「限量款智能手机」price=99.00 / **1002**「联名限量跑鞋」price=199.00，spu_id 对应 1/2)；演示 category(id 1) 与 brand(id 1) 各插一行（`category`/`brand` 无 Java 代码，用 JdbcTemplate 或 mapper 直插 —— 因无实体，采用 `JdbcTemplate` 插一行）；库存 `sku_id` 已为 1001/1002，与其对齐

- [ ] **Step 1: 实现**（仿 `StockDataInitializer`：以 `sku_code` 判空后插入；显式 id）
- [ ] **Step 2: 编译测试** 
- [ ] **Step 3: 提交** `git commit -m "feat(product): 演示数据预热"`

---

### Task 6: flash-inventory 去 sku_name

**Files:**
- Modify: `flash-inventory/src/main/java/com/flash/fulfill/inventory/entity/Stock.java:24`
- Modify: `flash-inventory/src/main/resources/mapper/StockMapper.xml:15`
- Modify: `flash-inventory/src/main/resources/schema.sql:4`
- Modify: `flash-inventory/src/main/java/com/flash/fulfill/inventory/config/StockDataInitializer.java`
- Modify: `flash-inventory/src/test/java/com/flash/fulfill/inventory/service/StockServiceTest.java`

**Interfaces:**
- Consumes: 无新
- Produces: `Stock` 无 `skuName`；`seedIfAbsent(Long skuId, int available)`

- [ ] **Step 1: 移除** `Stock.skuName`、`sku_name` 列与 SELECT 名；`seedIfAbsent` 改为 `(Long skuId, int available)`；同步测试
- [ ] **Step 2: 验证** `mvn -q -DskipTests package`；`mvn -q test -pl flash-inventory` PASS
- [ ] **Step 3: 提交** `git commit -m "refactor(inventory): 移除 sku_name，库存只存 skuId 与数量"`

---

### Task 7: flash-order 计价接入

**Files:**
- Create: `flash-order/src/main/java/com/flash/fulfill/order/feign/ProductClient.java`
- Modify: `flash-order/src/main/java/com/flash/fulfill/order/service/OrderService.java:42,63-71`
- Modify: `flash-order/src/main/resources/application.yml:37`
- Modify: `flash-order/src/test/java/com/flash/fulfill/order/service/OrderServiceTest.java`

**Interfaces:**
- Consumes: `GET /api/product/sku/{skuId}` → `Result<SkuSellView>`；`SkuSellView.price`
- Produces: `ProductClient.sellView(Long skuId)`（`@FeignClient(name="flash-product", contextId="productClient", path="/api/product")`）

- [ ] **Step 1: 失败测试** 建单按 SKU 真实价计价（mock ProductClient 返回 price=199.00）、SKU 缺失/下架→FAILED → FAIL
- [ ] **Step 2: 实现** `handleOrderCreate` 取 `SkuSellView v = productClient.sellView(cmd.getSkuId())`；`setAmount(v.getPrice() * qty)`；捕获异常/空 → FAILED；移除 `order.unit-price`（`OrderService.java` 的 `@Value` 与 `application.yml:37`）
- [ ] **Step 3: 运行测试** PASS
- [ ] **Step 4: 提交** `git commit -m "feat(order): 计价接入商品服务真实单价"`

---

### Task 8: flash-seckill 双状态校验

**Files:**
- Modify: `flash-seckill/pom.xml`（加 spring-cloud-starter-openfeign + spring-cloud-starter-loadbalancer）
- Modify: `flash-seckill/src/main/java/com/flash/fulfill/seckill/SeckillApplication.java`（`@EnableFeignClients`）
- Create: `flash-seckill/src/main/java/com/flash/fulfill/seckill/feign/ProductClient.java`
- Modify: `flash-seckill/src/main/java/com/flash/fulfill/seckill/service/SeckillService.java`
- Modify: `flash-seckill/src/test/java/com/flash/fulfill/seckill/service/SeckillServiceTest.java`

**Interfaces:**
- Consumes: `GET /api/product/sku/{skuId}` → `Result<SkuSellView>`；`SkuSellView.skuStatus` & `spuStatus`
- Produces: `ProductClient.sellView(Long skuId)`
- Validate: `skuStatus==1 && spuStatus==1`→放行；否则抛 `BizException(ErrorCode.PRODUCT_OFF_SHELF)`；SKU 不存在/调用异常 → `PRODUCT_NOT_FOUND`/按业务判定

- [ ] **Step 1: 失败测试** 下架/缺失抛错、上架放行（mock ProductClient）→ FAIL
- [ ] **Step 2: 实现** `validate(cmd)` 增加 `sellView` 校验
- [ ] **Step 3: 运行测试** PASS
- [ ] **Step 4: 提交** `git commit -m "feat(seckill): 秒抢校验商品 SPU/SKU 双上架状态"`

---

### Task 9: 网关路由

**Files:**
- Modify: `flash-gateway/src/main/resources/application.yml`（加 `flash-product-route`）

- [ ] **Step 1: 添加**

```yaml
        - id: flash-product-route
          uri: lb://flash-product
          predicates:
            - Path=/api/product/**
```

- [ ] **Step 2: 提交** `git commit -m "feat(gateway): 商品服务路由"`

---

### Task 10: 全量验证 + README

**Files:**
- Modify: `README.md`（模块表加 flash-product 8086、链路说明）

- [ ] **Step 1:** `mvn -q -DskipTests package`
- [ ] **Step 2:** `mvn -q test`
- [ ] **Step 3: 更新 README** + `git commit -m "docs: 商品服务说明"`
