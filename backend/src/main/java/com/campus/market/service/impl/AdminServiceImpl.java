package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.common.PageResult;
import com.campus.market.dto.request.ArbitrateRequest;
import com.campus.market.dto.request.AuditGoodsRequest;
import com.campus.market.dto.request.HandleReportRequest;
import com.campus.market.dto.response.DashboardVO;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.dto.response.UserVO;
import com.campus.market.entity.AuditLog;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Order;
import com.campus.market.entity.Report;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsEvent;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.OrderEvent;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.ReportStatus;
import com.campus.market.enums.ReportTargetType;
import com.campus.market.enums.Role;
import com.campus.market.enums.UserStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.AdminService;
import com.campus.market.service.AuditLogService;
import com.campus.market.statemachine.GoodsStateMachine;
import com.campus.market.statemachine.OrderStateMachine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台服务（FR-ADMIN-01~05；API-29~36）。
 * 审计（NFR-SEC-04）：全部管理动作经 AuditLogService.record 落库（TC-39 断言）。
 * 封禁联动（BR-08）：BANNED + 其在售商品同事务批量下架；解封商品不自动恢复（需手动上架）；
 * 不能封禁 ADMIN。治理下架口径：仅 ON_SALE 可被治理下架（LOCKED 归仲裁通道）。
 * 仲裁（FR-ADMIN-04）：强制完成复用 COMPLETE 事件；强制取消复用 ADMIN_CANCEL 事件，
 * 模拟凭证作废口径写入 cancel_reason。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final ReportMapper reportMapper;
    private final OrderMapper orderMapper;
    private final AuditLogService auditLogService;
    private final GoodsStateMachine goodsStateMachine;
    private final OrderStateMachine orderStateMachine;
    private final ObjectMapper objectMapper;

    // ---- API-29/30 商品审核（FR-ADMIN-01）----

    @Override
    public PageResult<GoodsVO> pendingGoods(Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);
        Page<Goods> result = goodsMapper.selectPage(new Page<>(p, s), new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, GoodsStatus.PENDING_AUDIT)
                .orderByAsc(Goods::getCreatedAt));                          // 先到先审
        List<GoodsVO> vos = result.getRecords().stream()
                .map(g -> GoodsVO.from(g, parseImages(g), null))
                .toList();
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    @Override
    @Transactional
    public GoodsVO auditGoods(Long adminId, Long goodsId, AuditGoodsRequest req) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品不存在或已删除");
        }
        boolean approve = "APPROVE".equals(req.action());
        if (!approve && (req.reason() == null || req.reason().isBlank())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "驳回原因必填");   // FR-ADMIN-01
        }
        GoodsStatus to = approve ? GoodsStatus.ON_SALE : GoodsStatus.REJECTED;
        goodsStateMachine.check(goods.getStatus(),
                approve ? GoodsEvent.APPROVE : GoodsEvent.REJECT);              // BR-02：仅 PENDING_AUDIT 可审
        long now = System.currentTimeMillis();
        int rows = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .eq(Goods::getStatus, GoodsStatus.PENDING_AUDIT)
                .set(Goods::getStatus, to)
                .set(Goods::getRejectReason, approve ? null : req.reason())
                .set(Goods::getPublishedAt, approve ? now : null)
                .set(Goods::getUpdatedAt, now));
        if (rows != 1) {
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID);             // 竞态兜底
        }
        auditLogService.record(adminId, "AUDIT_GOODS", "GOODS", goodsId,
                approve ? "APPROVE" : "REJECT:" + req.reason());
        goods.setStatus(to);
        if (approve) {
            goods.setPublishedAt(now);
        } else {
            goods.setRejectReason(req.reason());
        }
        return GoodsVO.from(goods, parseImages(goods), null);
    }

    // ---- API-31/32 举报处理（FR-ADMIN-02）----

    @Override
    public PageResult<Report> reports(String status, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            try {
                qw.eq(Report::getStatus, ReportStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.BAD_REQUEST, "举报状态非法");
            }
        }
        qw.orderByAsc(Report::getCreatedAt);
        Page<Report> result = reportMapper.selectPage(new Page<>(p, s), qw);
        return PageResult.of(result.getRecords(), result.getTotal(), p, s);
    }

    @Override
    @Transactional
    public void handleReport(Long adminId, Long reportId, HandleReportRequest req) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "举报单不存在");
        }
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该举报已处理");       // 幂等口径
        }
        boolean resolved = "RESOLVED".equals(req.result());
        if (!resolved && req.action() != null && !req.action().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不属实结论不可携带处理动作");
        }
        // 治理动作（RESOLVED 时，与举报状态更新同事务）
        if (resolved && "OFF_SHELF_GOODS".equals(req.action())) {
            if (report.getTargetType() != ReportTargetType.GOODS) {
                throw new BizException(ErrorCode.BAD_REQUEST, "该举报目标不是商品");
            }
            governanceOffShelfGoods(report.getTargetId());
        } else if (resolved && "BAN_USER".equals(req.action())) {
            if (report.getTargetType() != ReportTargetType.USER) {
                throw new BizException(ErrorCode.BAD_REQUEST, "该举报目标不是用户");
            }
            banUser(adminId, report.getTargetId(), true);                       // BR-08 联动（同事务）
        }
        int rows = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, reportId)
                .eq(Report::getStatus, ReportStatus.PENDING)
                .set(Report::getStatus, resolved ? ReportStatus.RESOLVED : ReportStatus.DISMISSED)
                .set(Report::getHandlerId, adminId)
                .set(Report::getHandleNote, nullSafe(req.note()))
                .set(Report::getHandledAt, System.currentTimeMillis()));
        if (rows != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该举报已处理");       // 竞态兜底
        }
        auditLogService.record(adminId, "HANDLE_REPORT", report.getTargetType().name(),
                report.getTargetId(), req.result() + (req.action() == null ? "" : ":" + req.action()));
    }

    private void governanceOffShelfGoods(Long goodsId) {
        int rows = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .eq(Goods::getStatus, GoodsStatus.ON_SALE)
                .set(Goods::getStatus, GoodsStatus.OFF_SHELF)
                .set(Goods::getUpdatedAt, System.currentTimeMillis()));
        if (rows != 1) {
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID, "商品当前不可治理下架");
        }
    }

    // ---- API-33/34 用户管理（FR-ADMIN-03，BR-08）----

    @Override
    public PageResult<UserVO> users(String keyword, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(User::getNickname, kw).or().like(User::getEmail, kw));
        }
        qw.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(new Page<>(p, s), qw);
        List<UserVO> vos = result.getRecords().stream().map(UserVO::from).toList();
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    @Override
    @Transactional
    public void banUser(Long adminId, Long userId, boolean banned) {
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户不存在");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能封禁管理员账号");   // FR-ADMIN-03
        }
        UserStatus from = banned ? UserStatus.ACTIVE : UserStatus.BANNED;
        UserStatus to = banned ? UserStatus.BANNED : UserStatus.ACTIVE;
        int rows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getStatus, from)
                .set(User::getStatus, to));
        if (rows != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, banned ? "用户已被封禁" : "用户未被封禁");
        }
        // BR-08：封禁联动其在售商品全部下架（同事务）；解封商品不自动恢复（需手动上架）
        if (banned) {
            goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getSellerId, userId)
                    .eq(Goods::getStatus, GoodsStatus.ON_SALE)
                    .set(Goods::getStatus, GoodsStatus.OFF_SHELF)
                    .set(Goods::getUpdatedAt, System.currentTimeMillis()));
        }
        auditLogService.record(adminId, banned ? "BAN_USER" : "UNBAN_USER",
                "USER", userId, nullSafe(target.getEmail()));
    }

    // ---- API-35 订单仲裁（FR-ADMIN-04）----

    @Override
    @Transactional
    public void arbitrateOrder(Long adminId, Long orderId, ArbitrateRequest req) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "订单不存在");
        }
        if ("COMPLETE".equals(req.action())) {
            // 强制完成：仅 PAID 可完成（PENDING_PAY 无完成语义，引导取消）+ 商品 SOLD
            orderStateMachine.check(order.getStatus(), OrderEvent.COMPLETE);
            if (orderMapper.markCompleted(orderId, System.currentTimeMillis()) != 1) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            }
            if (orderMapper.markSold(order.getGoodsId(), System.currentTimeMillis()) != 1) {
                throw new BizException(ErrorCode.GOODS_STATUS_INVALID);
            }
            auditLogService.record(adminId, "ARBITRATE_ORDER", "ORDER", orderId, "COMPLETE");
        } else if ("CANCEL".equals(req.action())) {
            // 强制取消：PENDING_PAY/PAID → CANCELLED（模拟凭证作废口径）+ 商品回滚在售
            orderStateMachine.check(order.getStatus(), OrderEvent.ADMIN_CANCEL);
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, orderId)
                    .in(Order::getStatus, OrderStatus.PENDING_PAY, OrderStatus.PAID)
                    .set(Order::getStatus, OrderStatus.CANCELLED)
                    .set(Order::getCancelReason, "管理员仲裁取消（模拟凭证作废）"));
            if (rows != 1) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            }
            if (orderMapper.unlockGoods(order.getGoodsId(), System.currentTimeMillis()) != 1) {
                throw new BizException(ErrorCode.GOODS_STATUS_INVALID);
            }
            auditLogService.record(adminId, "ARBITRATE_ORDER", "ORDER", orderId, "CANCEL");
        } else {
            throw new BizException(ErrorCode.BAD_REQUEST, "仲裁动作非法");
        }
    }

    // ---- API-36 统计看板（FR-ADMIN-05）----

    @Override
    public DashboardVO dashboard() {
        LocalDate today = LocalDate.now();
        long dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long userTotal = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, Role.USER));
        long userTodayNew = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreatedAt, dayStart));
        long goodsTotal = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getDeleted, 0));
        long goodsPendingAudit = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, GoodsStatus.PENDING_AUDIT));
        long orderTotal = orderMapper.selectCount(null);
        // 今日成交额：当日 COMPLETED 订单金额之和（内存聚合，目标规模可控）
        List<Order> todayCompleted = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.COMPLETED)
                .ge(Order::getCompleteTime, dayStart));
        double todayAmount = todayCompleted.stream().mapToDouble(Order::getAmount).sum();
        // 近 7 日订单趋势（按 created_at 逐日统计，走 idx_orders_timeout 无关、走主键扫描可接受）
        List<DashboardVO.DailyCount> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long ds = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long de = ds + Duration.ofDays(1).toMillis() - 1;
            Long c = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreatedAt, ds)
                    .le(Order::getCreatedAt, de));
            trend.add(new DashboardVO.DailyCount(day.toString(), c == null ? 0 : c));
        }
        return new DashboardVO(userTotal, userTodayNew, goodsTotal, goodsPendingAudit,
                orderTotal, todayAmount, trend);
    }

    private List<String> parseImages(Goods goods) {
        try {
            return objectMapper.readValue(goods.getImagesJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}