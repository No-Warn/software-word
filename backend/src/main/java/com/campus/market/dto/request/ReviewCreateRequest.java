package com.campus.market.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 交易评价请求（契约 API-25，FR-REVIEW-01，BR-07）。
 */
public record ReviewCreateRequest(
        @NotNull(message = "订单不能为空")
        Long orderId,
        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分为 1-5 星")
        @Max(value = 5, message = "评分为 1-5 星")
        Integer rating,
        @Size(max = 200, message = "评价内容不超过 200 字符")
        String content
) {
}