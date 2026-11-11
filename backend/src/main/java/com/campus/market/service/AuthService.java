package com.campus.market.service;

import com.campus.market.dto.request.LoginRequest;
import com.campus.market.dto.request.RegisterRequest;
import com.campus.market.dto.response.LoginVO;

/**
 * 认证服务（FR-AUTH-01/02/04；契约 API-01/02/06）。
 */
public interface AuthService {

    /** 注册（BR-01 校验 + 自动登录） */
    LoginVO register(RegisterRequest req);

    /** 登录（NFR-SEC-05 失败锁定；BANNED 拒绝） */
    LoginVO login(LoginRequest req);

    /** 修改密码：成功后旧 JWT 全量失效（FR-AUTH-04 / CH-007） */
    void changePassword(Long userId, String oldPassword, String newPassword);
}