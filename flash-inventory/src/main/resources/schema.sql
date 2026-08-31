CREATE TABLE IF NOT EXISTS stock (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id     BIGINT        NOT NULL COMMENT 'SKU ID',
    available  INT           NOT NULL DEFAULT 0 COMMENT '可用库存',
    locked     INT           NOT NULL DEFAULT 0 COMMENT '锁定库存',
    version    INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_id (sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='库存表';

CREATE TABLE IF NOT EXISTS stock_flow (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id VARCHAR(64)  NOT NULL COMMENT '幂等键(建单 requestId)',
    order_no   VARCHAR(32)  NOT NULL COMMENT '订单号',
    sku_id     BIGINT       NOT NULL COMMENT 'SKU ID',
    quantity   INT          NOT NULL COMMENT '扣减数量',
    result     VARCHAR(16)  NOT NULL COMMENT '结果 SUCCESS/FAILED',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='库存扣减流水表(防重复消费幂等)';