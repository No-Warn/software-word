package com.campus.market.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 举报请求（契约 API-27，FR-RPRT-01）：targetType=GOODS/USER；detail ≤200 字符（BR-11 不含举报）。
 */
public record ReportCreateRequest(
        @NotBlank(message = "举报类型不能为空")
        @Pattern(regexp = "GOODS|USER", message = "举报类型非法")
        String targetType,
        @NotNull(message = "举报目标不能为空")
        Long targetId,
        @NotBlank(message = "举报原因不能为空")
        String reason,
        @Size(max = 200, message = "补充说明不超过 200 字符")
        String detail
) {
}