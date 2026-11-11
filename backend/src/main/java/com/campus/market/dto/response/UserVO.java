package com.campus.market.dto.response;

import com.campus.market.entity.User;
import com.campus.market.enums.Role;
import com.campus.market.enums.UserStatus;

/**
 * 用户信息视图：不含 passwordHash 等敏感字段（NFR-SEC-01 衍生约束）。
 */
public record UserVO(
        Long id,
        String email,
        String nickname,
        String avatarUrl,
        String bio,
        Role role,
        UserStatus status
) {
    public static UserVO from(User u) {
        return new UserVO(u.getId(), u.getEmail(), u.getNickname(), u.getAvatarUrl(),
                u.getBio(), u.getRole(), u.getStatus());
    }
}