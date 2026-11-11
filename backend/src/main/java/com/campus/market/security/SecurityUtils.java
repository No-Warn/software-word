package com.campus.market.security;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户获取（02§2.6.2）：
 * JwtAuthFilter 注入 principal=userId(Long)、authority=ROLE_USER/ROLE_ADMIN。
 * 属主校验在 Service 层完成（双防线，NFR-SEC-02）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}