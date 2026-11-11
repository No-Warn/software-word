package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.market.enums.Role;
import com.campus.market.enums.UserStatus;
import lombok.Data;

/**
 * 用户实体（表结构 02§2.4.2 user，含 CH-007 增列 password_changed_at）。
 * 时间戳一律 epoch milliseconds（schema.sql 头部约定）。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    /** BCrypt(10) 哈希，禁止明文（NFR-SEC-01） */
    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String bio;

    private Role role;

    private UserStatus status;

    /** 连续登录失败次数（NFR-SEC-05，成功登录清零） */
    private Integer failCount;

    /** 登录锁定截止时间（epoch ms，null=未锁定） */
    private Long lockUntil;

    /** 最近改密时刻（epoch ms）；token.iat 早于它则全量失效（FR-AUTH-04，CH-007） */
    private Long passwordChangedAt;

    private Long createdAt;
}