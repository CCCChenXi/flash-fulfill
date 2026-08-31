CREATE TABLE IF NOT EXISTS flash_order (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(32)     NOT NULL COMMENT '订单号',
    user_id     BIGINT          NOT NULL COMMENT '用户ID',
    sku_id      BIGINT          NOT NULL COMMENT '商品SKU ID',
    activity_id BIGINT          DEFAULT NULL COMMENT '秒抢活动ID',
    quantity    INT             NOT NULL DEFAULT 1 COMMENT '购买数量',
    amount      DECIMAL(12, 2)  NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    status      VARCHAR(16)     NOT NULL COMMENT '状态 INITIAL/PENDING_PAYMENT/PENDING_SHIPMENT/SHIPPED/COMPLETED/CANCELLED',
    request_id  VARCHAR(64)     NOT NULL COMMENT '幂等键,秒抢客户端下发',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_request_id (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单表';

CREATE TABLE IF NOT EXISTS order_outbox (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id  VARCHAR(64)     NOT NULL COMMENT '幂等键,秒抢客户端下发',
    order_no    VARCHAR(32)     NOT NULL COMMENT '订单号',
    msg_type    VARCHAR(16)     NOT NULL COMMENT '消息类型 DEDUCT/FULFILL',
    msg_body    TEXT            NOT NULL COMMENT '消息载荷(JSON)',
    status      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/SENT',
    retry_count INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_msg (request_id, msg_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单本地消息表(事务性 Outbox)';

CREATE TABLE IF NOT EXISTS order_addresses (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id        BIGINT          NOT NULL COMMENT '订单ID(flash_order.id)',
    receiver_name   VARCHAR(32)     NOT NULL COMMENT '收货人姓名',
    receiver_phone  VARCHAR(20)     NOT NULL COMMENT '收货人手机号',
    province        VARCHAR(32)     NOT NULL COMMENT '省',
    city            VARCHAR(32)     NOT NULL COMMENT '市',
    district        VARCHAR(32)     NOT NULL COMMENT '区/县',
    detail_address  VARCHAR(128)    NOT NULL COMMENT '详细地址',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单收货地址表';