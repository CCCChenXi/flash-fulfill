package com.flash.fulfill.order.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单收货地址实体。
 * 与订单同一事务写入,order_id 唯一(一单一个地址)。
 */
@Data
@TableName("order_addresses")
public class OrderAddress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String receiverName;

    private String receiverPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    /** 由数据库 DEFAULT CURRENT_TIMESTAMP 维护,应用层不写 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}