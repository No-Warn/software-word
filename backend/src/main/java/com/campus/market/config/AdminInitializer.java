package com.campus.market.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.entity.User;
import com.campus.market.enums.Role;
import com.campus.market.enums.UserStatus;
import com.campus.market.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 管理员账号初始化（03§3.7）：首次启动若无 ADMIN 用户，
 * 以环境变量 ADMIN_PASSWORD 创建（BCrypt 落库，明文不落库不入代码库，NFR-MAINT-03）。
 * 登录邮箱固定 admin@campus.local（管理后台不走 BR-01 注册校验，直接落库）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final MarketProperties props;

    @Override
    public void run(ApplicationArguments args) {
        Long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, Role.ADMIN));
        if (adminCount != null && adminCount > 0) {
            log.info("admin already exists, skip initialization");
            return;
        }
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            log.warn("ADMIN_PASSWORD not set; admin account NOT created");
            return;
        }
        User admin = new User();
        admin.setEmail("admin@campus.local");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setNickname("admin");
        admin.setBio("平台管理员");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setFailCount(0);
        admin.setCreatedAt(System.currentTimeMillis());
        userMapper.insert(admin);
        log.info("admin account created (email=admin@campus.local)");
    }
}