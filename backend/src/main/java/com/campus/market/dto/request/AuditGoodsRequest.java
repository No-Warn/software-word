package com.campus.market.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商品审核请求（契约 API-30，FR-ADMIN-01）：驳回时 reason 必填（Service 校验）。
 */
public record AuditGoodsRequest(
        @NotBlank(message = "审核动作不能为空")
        @Pattern(regexp = "APPROVE|REJECT", message = "审核动作非法")
        String action,
        @Size(max = 200, message = "驳回原因不超过 200 字符")
        String reason
) {
}