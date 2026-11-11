package com.campus.market.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求（FR-AUTH-04）：成功后旧 JWT 全量失效（CH-007：iat 比对机制）。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空")
        String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度须为 8-64 字符")
        String newPassword
) {
}