package com.campus.market.security;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户获取（02§2.6.2）：JwtAuthFilter 注入 principal=userId(Long)、authority=ROLE_xxx。
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

    /**
     * 可选身份：匿名返回 null，已登录返回 userId。
     * 用于公开接口的属主判定（FR-GOODS-06：详情对属主开放非在售商品）。
     */
    public static Long optionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}