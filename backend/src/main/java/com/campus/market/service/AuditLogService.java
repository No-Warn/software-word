package com.campus.market.service;

import com.campus.market.entity.AuditLog;
import com.campus.market.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务（NFR-SEC-04）：全部管理动作（审核/封禁/仲裁/举报处理）落库，
 * 证据供 TC-39 断言。T-21 计划以 AOP 切面增强（02§2.7 @AdminAudited），本阶段显式调用。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public void record(Long operatorId, String action, String targetType, Long targetId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setOperatorId(operatorId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(detail);
        entry.setCreatedAt(System.currentTimeMillis());
        auditLogMapper.insert(entry);
    }
}