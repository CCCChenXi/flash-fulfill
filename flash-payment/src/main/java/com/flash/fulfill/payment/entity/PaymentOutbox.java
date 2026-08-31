package com.flash.fulfill.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付本地消息表(事务性 Outbox)。
 * 支付成功与写入同事务,由 PaymentOutboxRelay 轮询 Feign 回调订单 markPaid。
 */
@Data
@TableName("payment_outbox")
public class PaymentOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payNo;

    private String orderNo;

    /** MARK_PAID */
    private String msgType;

    private String msgBody;

    /** PENDING 待发送 / SENT 已发送 */
    private String status;

    private Integer retryCount;

    /** 由数据库 DEFAULT CURRENT_TIMESTAMP 维护,应用层不写 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}