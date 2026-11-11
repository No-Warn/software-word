package com.campus.market.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 个人资料修改请求（契约 API-05，FR-AUTH-03）：昵称 2-20 字符（01§1.6.1 验收口径）。
 */
public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(min = 2, max = 20, message = "昵称长度须为 2-20 字符")
        String nickname,
        @Size(max = 500, message = "头像地址过长")
        String avatarUrl,
        @Size(max = 200, message = "简介不超过 200 字符")
        String bio
) {
}