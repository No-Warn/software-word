package com.campus.market.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求（FR-AUTH-01）。邮箱后缀 BR-01 在 Service 校验（可配置白名单）。
 * 密码规则 8-64 字符为弱密码的可操作口径 [假设 A-16]，待确认后仅改注解。
 */
public record RegisterRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,
        @NotBlank(message = "昵称不能为空")
        @Size(min = 2, max = 20, message = "昵称长度须为 2-20 字符")
        String nickname,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度须为 8-64 字符")
        String password
) {
}