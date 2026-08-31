package com.flash.fulfill.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒抢下单响应(un queued)。
 */
@Data
public class FlashOrderResponse implements Serializable {

    /** 幂等键,客户端可凭此轮询订单结果 */
    private String requestId;
    private String message;

    /** 秒杀业务状态码:0 成功 / 1 参数非法 / 2 商品下架 / 3 库存不足 / 4 商品或库存不存在 */
    private Integer status;

    public FlashOrderResponse() {
    }

    public FlashOrderResponse(String requestId, String message) {
        this(requestId, message, null);
    }

    public FlashOrderResponse(String requestId, String message, Integer status) {
        this.requestId = requestId;
        this.message = message;
        this.status = status;
    }
}
