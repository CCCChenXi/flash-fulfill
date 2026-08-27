package com.flash.fulfill.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 库存扣减结果。
 */
@Data
public class DeductStockResult implements Serializable {

    private boolean success;
    /** 扣减后可用库存 */
    private Integer availableAfter;

    public DeductStockResult() {
    }

    public DeductStockResult(boolean success) {
        this.success = success;
    }

    public DeductStockResult(boolean success, Integer availableAfter) {
        this.success = success;
        this.availableAfter = availableAfter;
    }
}