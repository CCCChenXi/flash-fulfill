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