CREATE TABLE IF NOT EXISTS payment_record (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    pay_no      VARCHAR(32)     NOT NULL COMMENT '支付单号',
    order_no    VARCHAR(32)     NOT NULL COMMENT '订单号',
    user_id     BIGINT          NOT NULL COMMENT '用户ID',
    amount      DECIMAL(12, 2)  NOT NULL COMMENT '支付金额',
    channel     VARCHAR(16)     NOT NULL COMMENT '支付渠道',
    status      VARCHAR(16)     NOT NULL COMMENT '状态 INITIAL/PAID/FAILED/CANCELLED',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_no (pay_no),
    UNIQUE KEY uk_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='支付单表';

CREATE TABLE IF NOT EXISTS payment_outbox (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    pay_no      VARCHAR(32)     NOT NULL COMMENT '支付单号',
    order_no    VARCHAR(32)     NOT NULL COMMENT '订单号',
    msg_type    VARCHAR(16)     NOT NULL COMMENT '消息类型 MARK_PAID',
    msg_body    TEXT            NOT NULL COMMENT '消息载荷(JSON)',
    status      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/SENT',
    retry_count INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_no (pay_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='支付本地消息表(事务性 Outbox)';