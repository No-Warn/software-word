package com.campus.market.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 下单请求（契约 API-17：POST /orders {goodsId}）。
 */
public record OrderCreateRequest(
        @NotNull(message = "商品不能为空")
        Long goodsId
) {
}