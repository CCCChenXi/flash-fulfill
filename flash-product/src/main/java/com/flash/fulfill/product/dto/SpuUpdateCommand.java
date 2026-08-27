package com.flash.fulfill.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新 SPU 请求(部分更新,字段可选;null 视为不修改)。
 */
@Data
@NoArgsConstructor
public class SpuUpdateCommand {

    private String name;

    private Long categoryId;

    private Long brandId;

    private String description;

    private String mainImage;
}
