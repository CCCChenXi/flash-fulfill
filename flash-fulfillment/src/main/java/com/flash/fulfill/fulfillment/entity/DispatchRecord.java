package com.flash.fulfill.fulfillment.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单/履约记录。
 * 状态:已派单 DISPATCHED -> 已送达 DELIVERED(轨迹跟踪 TODO:接入物流服务回调/轨迹事件)。
 */
@Data
@TableName("dispatch_record")
public class DispatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long skuId;

    private Integer quantity;

    /** 派单仓库编码 */
    private String warehouseCode;

    /** 物流承运商 */
    private String carrierCode;

    /** 运单号 */
    private String trackingNo;

    /** 状态 DISPATCHED/DELIVERED */
    private String status;

    /** 由数据库 DEFAULT CURRENT_TIMESTAMP 维护,应用层不写 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
