package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.config.MarketProperties;
import com.campus.market.dto.request.LoginRequest;
import com.campus.market.dto.request.RegisterRequest;
import com.campus.market.dto.response.LoginVO;
import com.campus.market.dto.response.UserVO;
import com.campus.market.entity.User;
import com.campus.market.enums.Role;
import com.campus.market.enums.UserStatus;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.JwtUtil;
import com.campus.market.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 认证服务实现（FR-AUTH-01/02/04）。
 * 锁定策略 NFR-SEC-05（假设 A-02）：连续失败 5 次锁 10 分钟，成功登录清零；
 * 改密失效 FR-AUTH-04（CH-007）：更新 password_changed_at，过滤器按 token.iat 拦截旧 token；
 * 安全口径 NFR-SEC：BCrypt(10) 存储；错误统一文案防账号枚举。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    static final int MAX_FAIL_COUNT = 5;
    static final long LOCK_MINUTES = 10;
    static final String GENERIC_CREDENTIAL_ERROR = "邮箱或密码错误";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MarketProperties props;

    @Override
    public LoginVO register(RegisterRequest req) {
        // BR-01：匹配邮箱域名后缀，默认适配所有 *.edu.cn 高校域名。
        String email = req.email().toLowerCase(Locale.ROOT);
        String suffix = props.getCampusEmailSuffix().toLowerCase(Locale.ROOT);
        int atIndex = email.lastIndexOf('@');
        String domain = atIndex >= 0 ? email.substring(atIndex + 1) : "";
        if (!suffix.startsWith(".") || !domain.endsWith(suffix)) {
            throw new BizException(ErrorCode.EMAIL_SUFFIX_INVALID);
        }
        if (existsEmail(req.email())) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname());
        user.setBio("");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailCount(0);
        user.setCreatedAt(System.currentTimeMillis());
        userMapper.insert(user);
        return new LoginVO(jwtUtil.generate(user.getId(), user.getRole()), UserVO.from(user));
    }

    @Override
    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, req.email()));
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, GENERIC_CREDENTIAL_ERROR);
        }
        long now = System.currentTimeMillis();
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);           // A0240
        }
        if (user.getLockUntil() != null && user.getLockUntil() > now) {
            throw new BizException(ErrorCode.LOGIN_LOCKED);             // A0230（TC-02）
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int fails = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
            user.setFailCount(fails);
            if (fails >= MAX_FAIL_COUNT) {
                user.setLockUntil(now + Duration.ofMinutes(LOCK_MINUTES).toMillis());
                user.setFailCount(0);                                   // 锁定期满后重新计数
            }
            userMapper.updateById(user);
            throw new BizException(ErrorCode.UNAUTHORIZED, GENERIC_CREDENTIAL_ERROR);
        }
        if (user.getFailCount() != null && user.getFailCount() > 0) {
            user.setFailCount(0);
            user.setLockUntil(null);
            userMapper.updateById(user);
        }
        return new LoginVO(jwtUtil.generate(user.getId(), user.getRole()), UserVO.from(user));
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "旧密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(System.currentTimeMillis());          // CH-007：旧 token 全部失效
        userMapper.updateById(user);
    }

    private boolean existsEmail(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }
}