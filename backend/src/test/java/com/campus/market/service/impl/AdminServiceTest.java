package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.dto.request.ArbitrateRequest;
import com.campus.market.dto.request.AuditGoodsRequest;
import com.campus.market.dto.request.HandleReportRequest;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Order;
import com.campus.market.entity.Report;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.ReportStatus;
import com.campus.market.enums.ReportTargetType;
import com.campus.market.enums.Role;
import com.campus.market.enums.UserStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.AuditLogService;
import com.campus.market.statemachine.GoodsStateMachine;
import com.campus.market.statemachine.OrderStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台服务测试（FR-ADMIN-01~05、BR-08；TC-07/10/33/34/35/39 单元分支）。
 * DoD 核心：每管理动作落审计日志（verify auditLogService.record）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceTest {

    static {
        // MyBatis-Plus LambdaWrapper 依赖 JVM 级 TableInfo 缓存：纯 Mockito 单测无 Spring 上下文，
        // 需手动初始化（本类字母序最先执行，后续测试类亦受益）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Goods.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Report.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @Mock
    GoodsMapper goodsMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    ReportMapper reportMapper;
    @Mock
    OrderMapper orderMapper;
    @Mock
    AuditLogService auditLogService;

    AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminServiceImpl(goodsMapper, userMapper, reportMapper, orderMapper,
                auditLogService, new GoodsStateMachine(), new OrderStateMachine(), new ObjectMapper());
        when(goodsMapper.update(any(), any())).thenReturn(1);
        when(userMapper.update(any(), any())).thenReturn(1);
        when(reportMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.selectList(any())).thenReturn(List.of());
    }

    private Goods goods(GoodsStatus status) {
        Goods g = new Goods();
        g.setId(100L);
        g.setSellerId(2L);
        g.setTitle("待审核商品");
        g.setPrice(9.9);
        g.setImagesJson("[\"/api/v1/files/a.jpg\"]");
        g.setStatus(status);
        g.setDeleted(0);
        return g;
    }

    private User user(Role role, UserStatus status) {
        User u = new User();
        u.setId(3L);
        u.setEmail("stu1@stu.example.edu.cn");
        u.setNickname("张三");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    // ---- FR-ADMIN-01 商品审核（TC-07/TC-39）----

    @Test
    void audit_approve_setsOnSaleAndAudited() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.PENDING_AUDIT));

        var vo = service.auditGoods(9L, 100L, new AuditGoodsRequest("APPROVE", null));

        assertThat(vo.status()).isEqualTo(GoodsStatus.ON_SALE);
        // NFR-SEC-04 / TC-39：管理动作落审计日志
        verify(auditLogService).record(eq(9L), eq("AUDIT_GOODS"), eq("GOODS"), eq(100L), eq("APPROVE"));
    }

    @Test
    void audit_reject_requiresReason() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.PENDING_AUDIT));
        assertThatThrownBy(() -> service.auditGoods(9L, 100L, new AuditGoodsRequest("REJECT", null)))
                .hasMessageContaining("驳回原因必填");
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void audit_rejectsNonPending_B0201() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.ON_SALE));
        assertThatThrownBy(() -> service.auditGoods(9L, 100L, new AuditGoodsRequest("APPROVE", null)))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOODS_STATUS_INVALID);
    }

    // ---- FR-ADMIN-03 封禁（TC-10/TC-39，BR-08）----

    @Test
    void ban_bansUserAndOffShelvesGoods_BR08() {
        when(userMapper.selectById(3L)).thenReturn(user(Role.USER, UserStatus.ACTIVE));

        service.banUser(9L, 3L, true);

        verify(auditLogService).record(eq(9L), eq("BAN_USER"), eq("USER"), eq(3L), any());
        verify(goodsMapper).update(any(), any());       // BR-08：联动下架其在售商品
    }

    @Test
    void ban_rejectsAdminTarget() {
        when(userMapper.selectById(3L)).thenReturn(user(Role.ADMIN, UserStatus.ACTIVE));
        assertThatThrownBy(() -> service.banUser(9L, 3L, true))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void unban_goodsNotAutoRestored_BR08() {
        when(userMapper.selectById(3L)).thenReturn(user(Role.USER, UserStatus.BANNED));

        service.banUser(9L, 3L, false);

        verify(auditLogService).record(eq(9L), eq("UNBAN_USER"), eq("USER"), eq(3L), any());
        verify(goodsMapper, never()).update(any(), any());  // 解封商品不自动恢复，需手动上架
    }

    // ---- FR-ADMIN-02 举报处理（TC-33/TC-39）----

    private Report pendingReport(ReportTargetType type, Long targetId) {
        Report r = new Report();
        r.setId(200L);
        r.setReporterId(1L);
        r.setTargetType(type);
        r.setTargetId(targetId);
        r.setReason("违规");
        r.setStatus(ReportStatus.PENDING);
        r.setCreatedAt(System.currentTimeMillis());
        return r;
    }

    @Test
    void handleReport_resolvedOffShelfGoods() {
        when(reportMapper.selectById(200L)).thenReturn(pendingReport(ReportTargetType.GOODS, 100L));
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.ON_SALE));

        service.handleReport(9L, 200L, new HandleReportRequest("RESOLVED", "OFF_SHELF_GOODS", "属实"));

        verify(auditLogService).record(eq(9L), eq("HANDLE_REPORT"), eq("GOODS"), eq(100L), eq("RESOLVED:OFF_SHELF_GOODS"));
    }

    @Test
    void handleReport_resolvedBanUser_BR08() {
        when(reportMapper.selectById(200L)).thenReturn(pendingReport(ReportTargetType.USER, 3L));
        when(userMapper.selectById(3L)).thenReturn(user(Role.USER, UserStatus.ACTIVE));

        service.handleReport(9L, 200L, new HandleReportRequest("RESOLVED", "BAN_USER", "属实"));

        verify(auditLogService).record(eq(9L), eq("HANDLE_REPORT"), eq("USER"), eq(3L), eq("RESOLVED:BAN_USER"));
    }

    @Test
    void handleReport_dismissedWithAction_rejected() {
        when(reportMapper.selectById(200L)).thenReturn(pendingReport(ReportTargetType.GOODS, 100L));
        assertThatThrownBy(() -> service.handleReport(9L, 200L,
                new HandleReportRequest("DISMISSED", "OFF_SHELF_GOODS", null)))
                .hasMessageContaining("不可携带处理动作");
    }

    @Test
    void handleReport_alreadyHandled_rejected() {
        Report handled = pendingReport(ReportTargetType.GOODS, 100L);
        handled.setStatus(ReportStatus.RESOLVED);
        when(reportMapper.selectById(200L)).thenReturn(handled);
        assertThatThrownBy(() -> service.handleReport(9L, 200L,
                new HandleReportRequest("DISMISSED", null, null)))
                .hasMessageContaining("已处理");
    }

    // ---- FR-ADMIN-04 仲裁（TC-34/TC-39）----

    private Order order(OrderStatus status) {
        Order o = new Order();
        o.setId(50L);
        o.setOrderNo("ORD20261111120000123456");
        o.setGoodsId(10L);
        o.setBuyerId(1L);
        o.setSellerId(2L);
        o.setAmount(9.9);
        o.setStatus(status);
        o.setCreatedAt(System.currentTimeMillis());
        return o;
    }

    @Test
    void arbitrate_complete_fromPaid() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PAID));
        when(orderMapper.markCompleted(eq(50L), anyLong())).thenReturn(1);
        when(orderMapper.markSold(eq(10L), anyLong())).thenReturn(1);

        service.arbitrateOrder(9L, 50L, new ArbitrateRequest("COMPLETE"));

        verify(auditLogService).record(eq(9L), eq("ARBITRATE_ORDER"), eq("ORDER"), eq(50L), eq("COMPLETE"));
    }

    @Test
    void arbitrate_complete_fromPendingPay_rejected_B0304() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PENDING_PAY));
        assertThatThrownBy(() -> service.arbitrateOrder(9L, 50L, new ArbitrateRequest("COMPLETE")))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
    }

    @Test
    void arbitrate_cancel_fromPendingOrPaid_withVoucherVoided() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PAID));
        when(orderMapper.unlockGoods(eq(10L), anyLong())).thenReturn(1);

        service.arbitrateOrder(9L, 50L, new ArbitrateRequest("CANCEL"));

        verify(auditLogService).record(eq(9L), eq("ARBITRATE_ORDER"), eq("ORDER"), eq(50L), eq("CANCEL"));
        verify(orderMapper).unlockGoods(eq(10L), anyLong());
    }

    // ---- FR-ADMIN-05 看板（TC-35 结构口径）----

    @Test
    void dashboard_returnsAllMetrics() {
        when(userMapper.selectCount(any())).thenReturn(10L);
        when(goodsMapper.selectCount(any())).thenReturn(20L);
        when(orderMapper.selectCount(any())).thenReturn(5L);
        when(orderMapper.selectCount((com.baomidou.mybatisplus.core.conditions.Wrapper<Order>) eq(null))).thenReturn(5L);
        when(orderMapper.selectList(any())).thenReturn(List.of());

        var vo = service.dashboard();

        assertThat(vo.userTotal()).isEqualTo(10);
        assertThat(vo.goodsTotal()).isEqualTo(20);
        assertThat(vo.orderTotal()).isEqualTo(5);
        assertThat(vo.orderTrend7d()).hasSize(7);       // 近 7 日趋势
    }
}