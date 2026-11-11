package com.campus.market.security;

import com.campus.market.entity.User;
import com.campus.market.enums.UserStatus;
import com.campus.market.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器（设计 02§2.6.2 / NFR-SEC-02）：
 * 1) 解析 Bearer token；
 * 2) 校验用户存在且 ACTIVE（BANNED 用户已发 token 立即失效，BR-08 强化）；
 * 3) 校验 token.iat ≥ password_changed_at（改密后旧 JWT 全量失效，FR-AUTH-04 / CH-007）；
 * 4) 注入 principal=userId(Long)、authority=ROLE_xxx。
 * 校验失败时静默跳过（无 SecurityContext），由授权规则统一返回 401。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtUtil.parse(header.substring(7));
            if (claims != null && claims.getSubject() != null) {
                Long userId = Long.valueOf(claims.getSubject());
                Long issuedAt = claims.getIssuedAt() == null ? 0L : claims.getIssuedAt().getTime();
                User user = userMapper.selectById(userId);
                if (user != null
                        && user.getStatus() == UserStatus.ACTIVE
                        && (user.getPasswordChangedAt() == null || issuedAt >= user.getPasswordChangedAt())) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, authorities));
                }
            }
        }
        chain.doFilter(request, response);
    }
}