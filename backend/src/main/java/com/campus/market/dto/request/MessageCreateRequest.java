package com.campus.market.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 留言请求（契约 API-22，FR-MSG-01/02）：content ≤200 字符；parentId 可选（一层回复）。
 * BR-11 敏感词过滤在 Service 层（B0401）。
 */
public record MessageCreateRequest(
        @NotBlank(message = "留言内容不能为空")
        @Size(max = 200, message = "留言内容不超过 200 字符")
        String content,
        Long parentId
) {
}