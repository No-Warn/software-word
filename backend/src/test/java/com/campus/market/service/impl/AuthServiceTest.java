package com.campus.market.service.impl;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.config.MarketProperties;
import com.campus.market.dto.request.LoginRequest;
import com.campus.market.dto.request.RegisterRequest;
import com.campus.market.entity.User;
import com.campus.market.enums.UserStatus;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试（FR-AUTH-01/02/04、BR-01、NFR-SEC-05；对应 TC-01/TC-02）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    UserMapper userMapper;
    @Mock
    PasswordEncoder encoder;

    AuthServiceImpl service;
    User user;

    @BeforeEach
    void setUp() {
        MarketProperties props = new MarketProperties();
        props.setCampusEmailSuffix("@stu.example.edu.cn");
        props.getJwt().setSecret("test-secret-test-secret-test-secret-123456");
        AuthServiceImpl realService = new AuthServiceImpl(userMapper, encoder,
                new JwtUtil(props), props);
        // service 字段为 final，直接使用真实实例
        service = realService;
        user = new User();
        user.setId(1L);
        user.setEmail("stu1@stu.example.edu.cn");
        user.setPasswordHash("$2a$10$stored");
        user.setNickname("张三");
        user.setRole(com.campus.market.enums.Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailCount(0);
    }

    // ---- TC-01：BR-01 三分支 ----

    @Test
    void register_rejectsNonCampusSuffix_B0101() {
        var req = new RegisterRequest("someone@gmail.com", "张三", "abcd1234");
        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_SUFFIX_INVALID);
    }

    @Test
    void register_rejectsDuplicateEmail_B0102() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        var req = new RegisterRequest("stu1@stu.example.edu.cn", "张三", "abcd1234");
        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_EXISTS);
    }

    @Test
    void register_ok_storesBcryptHashAndReturnsToken() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode("abcd1234")).thenReturn("$2a$10$encoded");
        var req = new RegisterRequest("stu2@stu.example.edu.cn", "李四", "abcd1234");

        var vo = service.register(req);

        assertThat(vo.token()).isNotBlank();
        assertThat(vo.userInfo().email()).isEqualTo("stu2@stu.example.edu.cn");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        // NFR-SEC-01：库内仅存 BCrypt 哈希，无明文
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$encoded");
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("abcd1234");
    }

    // ---- TC-02：NFR-SEC-05 连续失败 5 次锁定 ----

    @Test
    void login_locksAfter5ConsecutiveFailures() {
        when(userMapper.selectOne(any())).thenReturn(user);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        var req = new LoginRequest("stu1@stu.example.edu.cn", "wrong");

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> service.login(req))
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }
        // 第 5 次失败后：lockUntil 已设置且 failCount 归零（锁定期满后重新计数）
        assertThat(user.getLockUntil()).isNotNull();
        assertThat(user.getFailCount()).isZero();

        // 第 6 次（锁定期内）：A0230
        assertThatThrownBy(() -> service.login(req))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_LOCKED);
        verify(userMapper, times(5)).updateById(any(User.class));
    }

    @Test
    void login_bannedRejected_A0240() {
        user.setStatus(UserStatus.BANNED);
        when(userMapper.selectOne(any())).thenReturn(user);
        var req = new LoginRequest("stu1@stu.example.edu.cn", "abcd1234");

        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_BANNED);
    }

    @Test
    void login_wrongPassword_genericMessage() {
        when(userMapper.selectOne(any())).thenReturn(user);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("stu1@stu.example.edu.cn", "bad")))
                .hasMessageContaining("邮箱或密码错误");
    }

    // ---- TC-17：FR-AUTH-04 改密 ----

    @Test
    void changePassword_updatesTimestampForTokenInvalidation() {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(encoder.matches("oldPass1", "$2a$10$stored")).thenReturn(true);
        when(encoder.encode("newPass99")).thenReturn("$2a$10$new");

        service.changePassword(1L, "oldPass1", "newPass99");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPasswordChangedAt()).isNotNull();
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$new");
    }

    @Test
    void changePassword_wrongOldPasswordRejected() {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(1L, "bad", "newPass99"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("旧密码错误");
    }
}