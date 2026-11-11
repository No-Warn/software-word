package com.campus.market.controller;

import com.campus.market.common.PageResult;
import com.campus.market.common.Result;
import com.campus.market.dto.request.ChangePasswordRequest;
import com.campus.market.dto.request.UpdateProfileRequest;
import com.campus.market.dto.response.MessageVO;
import com.campus.market.dto.response.MyGoodsVO;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.dto.response.ReportVO;
import com.campus.market.dto.response.UserProfileVO;
import com.campus.market.dto.response.UserVO;
import com.campus.market.enums.TradeRole;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.AuthService;
import com.campus.market.service.GoodsQueryService;
import com.campus.market.service.MessageService;
import com.campus.market.service.OrderService;
import com.campus.market.service.ReportService;
import com.campus.market.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（契约 02§2.5.2）：API-04/05 资料（T-04）、API-06 改密（T-03）、API-07 主页（T-04）、
 * API-16 我的商品（T-07）、API-21 我的订单（T-10）、API-24 我的留言（T-12）、API-28 我的举报（T-14）。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final GoodsQueryService goodsQueryService;
    private final OrderService orderService;
    private final ReportService reportService;
    private final UserService userService;
    private final MessageService messageService;

    // ---- API-04/05 资料（FR-AUTH-03）----

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.me(SecurityUtils.currentUserId()));
    }

    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(userService.updateMe(SecurityUtils.currentUserId(), req));
    }

    // ---- API-07 他人主页（FR-REVIEW-02：信用分 + 在售商品）----

    @GetMapping("/{id}/profile")
    public Result<UserProfileVO> profile(@PathVariable Long id) {
        return Result.ok(userService.profile(id));
    }

    // ---- API-06 改密（FR-AUTH-04）----

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(SecurityUtils.currentUserId(), req.oldPassword(), req.newPassword());
        return Result.ok();
    }

    // ---- API-16 我的商品（FR-GOODS-08）----

    @GetMapping("/me/goods")
    public Result<MyGoodsVO> myGoods() {
        return Result.ok(goodsQueryService.myGoods(SecurityUtils.currentUserId()));
    }

    // ---- API-21 我的订单（FR-ORDER-06）----

    @GetMapping("/me/orders")
    public Result<PageResult<OrderVO>> myOrders(
            @RequestParam(defaultValue = "BUYER") String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        TradeRole tradeRole;
        try {
            tradeRole = TradeRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            tradeRole = TradeRole.BUYER;        // 非法维度宽松降级（FR-GOODS-07 同口径）
        }
        return Result.ok(orderService.listMine(SecurityUtils.currentUserId(), tradeRole, page, size));
    }

    // ---- API-24 我的留言（FR-MSG-03）----

    @GetMapping("/me/messages")
    public Result<PageResult<MessageVO>> myMessages(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(messageService.mine(SecurityUtils.currentUserId(), page, size));
    }

    // ---- API-28 我的举报（FR-RPRT-02）----

    @GetMapping("/me/reports")
    public Result<PageResult<ReportVO>> myReports(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(reportService.listMine(SecurityUtils.currentUserId(), page, size));
    }
}