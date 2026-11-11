package com.campus.market.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流过滤器（02§2.5.1）：登录/注册/发表留言接口单 IP 60 次/分钟（内存滑动窗口，单机足够）。
 * 超限返回 HTTP 429 + A0429（契约外补充码，CH-010 登记）。
 * 仅拦截高频写入口，浏览类 GET 不限流（NFR-PERF-01 由压测保障）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    static final int LIMIT_PER_MINUTE = 60;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isLimitedEndpoint(request)) {
            String ip = request.getRemoteAddr();
            Deque<Long> window = buckets.computeIfAbsent(ip, k -> new ArrayDeque<>());
            long now = System.currentTimeMillis();
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= LIMIT_PER_MINUTE) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":\"A0429\",\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return;
            }
            window.addLast(now);
        }
        chain.doFilter(request, response);
    }

    private boolean isLimitedEndpoint(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.matches("/api/v1/goods/\\d+/messages");
    }
}