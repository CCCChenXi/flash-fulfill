package com.flash.fulfill.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建 SPU 请求。
 */
@Data
@NoArgsConstructor
public class SpuCreateCommand {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    @NotNull(message = "类目 ID 不能为空")
    private Long categoryId;

    private Long brandId;

    private String description;

    private String mainImage;
}
