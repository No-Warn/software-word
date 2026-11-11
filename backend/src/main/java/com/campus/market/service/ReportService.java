package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.request.ReportCreateRequest;
import com.campus.market.dto.response.ReportVO;

/**
 * 举报服务（FR-RPRT-01/02；契约 API-27/28）。管理侧处理（API-31/32）归 T-15（已交付）。
 */
public interface ReportService {

    /** 举报受理（FR-RPRT-01）：目标存在性校验 + 重复举报拒绝（B0403） */
    ReportVO create(Long reporterId, ReportCreateRequest req);

    /** 我的举报（FR-RPRT-02）：含处理状态与结论 */
    PageResult<ReportVO> listMine(Long userId, Integer page, Integer size);
}