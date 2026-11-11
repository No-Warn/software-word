package com.campus.market.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 订单仲裁请求（契约 API-35，FR-ADMIN-04）。
 */
public record ArbitrateRequest(
        @NotBlank(message = "仲裁动作不能为空")
        @Pattern(regexp = "COMPLETE|CANCEL", message = "仲裁动作非法")
        String action
) {
}