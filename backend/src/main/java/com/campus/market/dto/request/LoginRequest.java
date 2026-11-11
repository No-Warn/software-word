package com.campus.market.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求（FR-AUTH-02）。错误统一文案"邮箱或密码错误"，防账号枚举。
 */
public record LoginRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,
        @NotBlank(message = "密码不能为空")
        String password
) {
}