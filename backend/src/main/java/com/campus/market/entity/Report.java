package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.market.enums.ReportStatus;
import com.campus.market.enums.ReportTargetType;
import lombok.Data;

/**
 * 举报实体（表结构 02§4.2 report）。受理（API-27，FR-RPRT-01）归 T-14；
 * 管理侧处理（API-31/32，FR-ADMIN-02）由本实体承载，T-15 建。
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    private ReportTargetType targetType;

    private Long targetId;

    private String reason;

    private String detail;

    private ReportStatus status;

    private Long handlerId;

    private String handleNote;

    private Long handledAt;

    private Long createdAt;
}