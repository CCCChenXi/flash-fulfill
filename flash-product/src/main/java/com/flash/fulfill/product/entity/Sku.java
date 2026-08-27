package com.flash.fulfill.product.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU 商品实体。
 * status: 0 下架,1 上架。specs 以 JSON 字符串存储。
 */
@Data
@TableName("sku")
public class Sku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spuId;

    private String skuCode;

    private String name;

    private BigDecimal price;

    private String image;

    /** JSON 字符串,如 {"color":"黑","storage":"256G"} */
    private String specs;

    private Integer status;

    /** 由数据库 DEFAULT CURRENT_TIMESTAMP 维护,应用层不写 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
