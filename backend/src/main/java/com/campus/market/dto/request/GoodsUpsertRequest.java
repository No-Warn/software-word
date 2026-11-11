package com.campus.market.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 商品发布/编辑请求（FR-GOODS-01/02；BR-09 价格、BR-10 图片 1~9 张）。
 * 注解为 HTTP 层用户友好提示；Service 内再做防御校验（保证非 HTTP 入口同样受限）。
 */
public record GoodsUpsertRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 60, message = "标题不超过 60 字符")
        String title,
        @Size(max = 2000, message = "描述不超过 2000 字符")
        String description,
        @NotNull(message = "分类不能为空")
        Long categoryId,
        @NotBlank(message = "成色不能为空")
        @Pattern(regexp = "NEW|LIKE_NEW|GOOD|WELL_USED", message = "成色取值非法")
        String condition,
        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.01", message = "价格最低 0.01 元")
        @DecimalMax(value = "99999.99", message = "价格最高 99999.99 元")
        Double price,
        @NotEmpty(message = "至少上传 1 张图片")
        @Size(max = 9, message = "图片最多 9 张")
        List<String> images
) {
}