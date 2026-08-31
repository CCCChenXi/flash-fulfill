package com.flash.fulfill.order.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单本地消息表(事务性 Outbox)。
 * <p>
 * 与订单写入同一事务,由 flash-relay 轮询转发 RocketMQ:
 * msg_type=DEDUCT  → INVENTORY_DEDUCT(扣库存命令)
 * msg_type=FULFILL → ORDER_FULFILL(履约事件)
 */
@Data
@TableName("order_outbox")
public class OrderOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private String orderNo;

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