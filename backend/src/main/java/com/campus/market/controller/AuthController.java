package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.request.LoginRequest;
import com.campus.market.dto.request.RegisterRequest;
import com.campus.market.dto.response.LoginVO;
import com.campus.market.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（契约 API-01/02/03，02§2.5.2）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // 无状态 JWT：登出由前端清除令牌（假设 A-11：服务端黑名单默认关闭）
        return Result.ok();
    }
}