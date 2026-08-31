package com.flash.fulfill.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 订单确认命令(客户端在订单详情页填地址后提交)。
 * 服务端同步更新订单并写入收货地址表。
 */
@Data
public class OrderConfirmCommand {

    @NotBlank(message = "receiverName 为必填")
    private String receiverName;

    @NotBlank(message = "receiverPhone 为必填")
    private String receiverPhone;

    @NotBlank(message = "province 为必填")
    private String province;

    @NotBlank(message = "city 为必填")
    private String city;

    @NotBlank(message = "district 为必填")
    private String district;

    @NotBlank(message = "detailAddress 为必填")
    private String detailAddress;
}