package com.campus.market.dto.response;

import com.campus.market.entity.Report;
import com.campus.market.enums.ReportStatus;
import com.campus.market.enums.ReportTargetType;

/**
 * 举报视图（契约 API-27/28，FR-RPRT-01/02）：处理后状态与结论可见（handleNote/handledAt）。
 */
public record ReportVO(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        String reason,
        String detail,
        ReportStatus status,
        String handleNote,
        Long handledAt,
        Long createdAt
) {
    public static ReportVO from(Report r) {
        return new ReportVO(r.getId(), r.getTargetType(), r.getTargetId(), r.getReason(),
                r.getDetail(), r.getStatus(), r.getHandleNote(), r.getHandledAt(), r.getCreatedAt());
    }
}