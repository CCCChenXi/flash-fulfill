CREATE TABLE IF NOT EXISTS flash_order (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(32)     NOT NULL COMMENT '订单号',
    user_id     BIGINT          NOT NULL COMMENT '用户ID',
    sku_id      BIGINT          NOT NULL COMMENT '商品SKU ID',
    activity_id BIGINT          DEFAULT NULL COMMENT '秒抢活动ID',
    quantity    INT             NOT NULL DEFAULT 1 COMMENT '购买数量',
    amount      DECIMAL(12, 2)  NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    status      VARCHAR(16)     NOT NULL COMMENT '状态 INITIAL/CREATED/FAILED/DISPATCHED/CLOSED',
    request_id  VARCHAR(64)     NOT NULL COMMENT '幂等键,秒抢客户端下发',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_request_id (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单表';