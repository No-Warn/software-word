package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.request.ArbitrateRequest;
import com.campus.market.dto.request.AuditGoodsRequest;
import com.campus.market.dto.request.HandleReportRequest;
import com.campus.market.dto.response.DashboardVO;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.dto.response.UserVO;
import com.campus.market.entity.Report;

/**
 * 管理后台服务（FR-ADMIN-01~05；契约 API-29~36）。
 * 全部动作落审计日志（NFR-SEC-04，TC-39）；封禁/仲裁带同事务联动（02§2.6.6，BR-08）。
 * 角色拦截由 SecurityConfig /api/v1/admin/** hasAuthority(ROLE_ADMIN) + @PreAuthorize 双保险。
 */
public interface AdminService {

    /** 待审核商品队列（API-29） */
    PageResult<GoodsVO> pendingGoods(Integer page, Integer size);

    /** 商品审核（API-30，FR-ADMIN-01）：通过→ON_SALE；驳回原因必填→REJECTED */
    GoodsVO auditGoods(Long adminId, Long goodsId, AuditGoodsRequest req);

    /** 举报队列（API-31）：status 缺省 PENDING */
    PageResult<Report> reports(String status, Integer page, Integer size);

    /** 举报处理（API-32，FR-ADMIN-02）：属实可下架商品/封禁用户（BR-08） */
    void handleReport(Long adminId, Long reportId, HandleReportRequest req);

    /** 用户检索（API-33）：昵称/邮箱模糊 */
    PageResult<UserVO> users(String keyword, Integer page, Integer size);

    /** 封禁/解封（API-34，FR-ADMIN-03）：封禁联动其在售商品全下架（BR-08） */
    void banUser(Long adminId, Long userId, boolean banned);

    /** 订单仲裁（API-35，FR-ADMIN-04）：强制完成/取消（模拟凭证作废口径） */
    void arbitrateOrder(Long adminId, Long orderId, ArbitrateRequest req);

    /** 统计看板（API-36，FR-ADMIN-05） */
    DashboardVO dashboard();
}