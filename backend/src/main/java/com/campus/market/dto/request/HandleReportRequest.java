package com.campus.market.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 举报处理请求（契约 API-32，FR-ADMIN-02）：
 * result=RESOLVED（属实）时 action 可选 OFF_SHELF_GOODS / BAN_USER / 空（仅标记属实）；
 * result=DISMISSED（不属实）时 action 须为空（Service 校验）。
 */
public record HandleReportRequest(
        @NotBlank(message = "处理结论不能为空")
        @Pattern(regexp = "RESOLVED|DISMISSED", message = "处理结论非法")
        String result,
        @Pattern(regexp = "OFF_SHELF_GOODS|BAN_USER", message = "处理动作非法")
        String action,
        @Size(max = 200, message = "处理说明不超过 200 字符")
        String note
) {
}