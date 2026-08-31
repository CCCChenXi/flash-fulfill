package com.flash.fulfill.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体。order_no 唯一(一单一支付),幂等依据。
 */
@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payNo;

    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    private String channel;

    /** INITIAL / PAID / FAILED / CANCELLED */
    private String status;

    /** 由数据库 DEFAULT CURRENT_TIMESTAMP 维护,应用层不写 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}