package com.campus.market.config;

import com.campus.market.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（设计 02§2.6.2 / NFR-SEC-02；路径规则来源：需求 01§1.4.2 权限矩阵）：
 * - 公开：/api/v1/auth/**、浏览类 GET（商品列表/详情/留言、他人主页、文件读取）、actuator
 * - 管理：/api/v1/admin/** 需 ROLE_ADMIN（FR-ADMIN-*）
 * - 其余接口需登录；资源属主校验在 Service 层完成（双防线）；
 * - 越权访问他人资源由 Service 抛 FORBIDDEN（A0302，NFR-SEC-02）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);   // NFR-SEC-01
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/goods/**",
                        "/api/v1/users/*/profile",
                        "/api/v1/files/**"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(eh -> eh
                // 契约 02§2.5.3：未登录/凭证失效→A0301(401)；越权→A0302(403)。
                // Spring Security 6 默认 EntryPoint 对匿名请求返回 403，须显式对齐
                .authenticationEntryPoint((req, resp, ex) -> {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"code\":\"A0301\",\"message\":\"未登录或凭证失效\",\"data\":null}");
                })
                .accessDeniedHandler((req, resp, ex) -> {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"code\":\"A0302\",\"message\":\"无权限\",\"data\":null}");
                }))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}