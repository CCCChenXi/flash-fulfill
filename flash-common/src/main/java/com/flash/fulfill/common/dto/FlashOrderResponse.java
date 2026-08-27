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

    public FlashOrderResponse() {
    }

    public FlashOrderResponse(String requestId, String message) {
        this.requestId = requestId;
        this.message = message;
    }
}