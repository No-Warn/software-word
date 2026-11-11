package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.request.ChangePasswordRequest;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（契约 02§2.5.2）。
 * 本类先交付改密 API-06（FR-AUTH-04，T-03）；/users/me 资料读写归 T-04 增量补充。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(SecurityUtils.currentUserId(), req.oldPassword(), req.newPassword());
        return Result.ok();
    }
}