package com.campus.market.controller;

import com.campus.market.common.PageResult;
import com.campus.market.common.Result;
import com.campus.market.dto.request.ArbitrateRequest;
import com.campus.market.dto.request.AuditGoodsRequest;
import com.campus.market.dto.request.HandleReportRequest;
import com.campus.market.dto.response.DashboardVO;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.dto.response.UserVO;
import com.campus.market.entity.Report;
import com.campus.market.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台接口（契约 API-29~36，02§2.5.2）。
 * 安全双保险：SecurityConfig 对 /api/v1/admin/** 强制 ROLE_ADMIN + 本类级 @PreAuthorize（NFR-SEC-02）。
 * 全部动作落审计日志（NFR-SEC-04，TC-39）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/goods/pending")
    public Result<PageResult<GoodsVO>> pendingGoods(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(adminService.pendingGoods(page, size));
    }

    @PostMapping("/goods/{id}/audit")
    public Result<GoodsVO> auditGoods(@PathVariable Long id, @Valid @RequestBody AuditGoodsRequest req) {
        return Result.ok(adminService.auditGoods(
                com.campus.market.security.SecurityUtils.currentUserId(), id, req));
    }

    @GetMapping("/reports")
    public Result<PageResult<Report>> reports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(adminService.reports(status, page, size));
    }

    @PostMapping("/reports/{id}/handle")
    public Result<Void> handleReport(@PathVariable Long id, @Valid @RequestBody HandleReportRequest req) {
        adminService.handleReport(com.campus.market.security.SecurityUtils.currentUserId(), id, req);
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<PageResult<UserVO>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(adminService.users(keyword, page, size));
    }

    @PostMapping("/users/{id}/ban")
    public Result<Void> banUser(@PathVariable Long id, @RequestParam boolean banned) {
        adminService.banUser(com.campus.market.security.SecurityUtils.currentUserId(), id, banned);
        return Result.ok();
    }

    @PostMapping("/orders/{id}/arbitrate")
    public Result<Void> arbitrateOrder(@PathVariable Long id, @Valid @RequestBody ArbitrateRequest req) {
        adminService.arbitrateOrder(com.campus.market.security.SecurityUtils.currentUserId(), id, req);
        return Result.ok();
    }

    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.ok(adminService.dashboard());
    }
}