CREATE TABLE IF NOT EXISTS dispatch_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no        VARCHAR(32)  NOT NULL COMMENT '订单号',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    sku_id          BIGINT       NOT NULL COMMENT 'SKU ID',
    quantity        INT          NOT NULL DEFAULT 1 COMMENT '数量',
    warehouse_code  VARCHAR(16)  NOT NULL COMMENT '派单仓库编码',
    carrier_code    VARCHAR(32)  DEFAULT NULL COMMENT '物流承运商',
    tracking_no     VARCHAR(32)  DEFAULT NULL COMMENT '运单号',
    status          VARCHAR(16)  NOT NULL COMMENT '状态 DISPATCHED/DELIVERED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='派单履约记录表';