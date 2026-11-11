package com.campus.market.dto.response;

import java.util.List;

/**
 * 统计看板视图（契约 API-36，FR-ADMIN-05）：数字与库内统计口径一致（误差 0）。
 * 今日成交额 = 当日状态变为 COMPLETED 的订单金额之和（需求 1.7.3）。
 */
public record DashboardVO(
        long userTotal,
        long userTodayNew,
        long goodsTotal,
        long goodsPendingAudit,
        long orderTotal,
        double todayAmount,
        List<DailyCount> orderTrend7d
) {
    public record DailyCount(String date, long count) {
    }
}