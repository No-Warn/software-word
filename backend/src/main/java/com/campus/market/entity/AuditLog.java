package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 管理操作审计日志（NFR-SEC-04）：审核/封禁/仲裁/举报处理全部落库。
 * T-21 计划以 AOP 切面（@AdminAudited）增强，本阶段为显式调用 AuditLogService.record。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作管理员 user.id */
    private Long operatorId;

    /** AUDIT_GOODS / BAN_USER / UNBAN_USER / ARBITRATE_ORDER / HANDLE_REPORT */
    private String action;

    private String targetType;

    private Long targetId;

    private String detail;

    private Long createdAt;
}