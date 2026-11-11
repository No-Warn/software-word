package com.campus.market.service.impl;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.config.MarketProperties;
import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Order;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.OrderEvent;
import com.campus.market.enums.OrderStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.OrderService;
import com.campus.market.statemachine.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单生命周期测试（FR-ORDER-03/05；TC-25/TC-26 单元分支；02§2.6.6 事务一致性兜底）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderLifecycleTest {

    @Mock
    OrderMapper orderMapper;
    @Mock
    GoodsMapper goodsMapper;
    @Mock
    UserMapper userMapper;

    OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderMapper, goodsMapper, userMapper,
                new OrderStateMachine(), new MarketProperties());
    }

    private Order order(OrderStatus status) {
        Order o = new Order();
        o.setId(50L);
        o.setOrderNo("ORD20261111120000123456");
        o.setGoodsId(10L);
        o.setBuyerId(1L);
        o.setSellerId(2L);
        o.setAmount(9.9);
        o.setStatus(status);
        o.setCreatedAt(System.currentTimeMillis() - 1000);
        return o;
    }

    // ---- FR-ORDER-03 取消（TC-25 单元分支）----

    @Test
    void cancel_rejectsPaidOrder_B0304() {
        // PAID 只能仲裁取消（01§1.5.3），买家不可取消
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PAID));
        assertThatThrownBy(() -> service.cancel(1L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        verify(orderMapper, never()).markCancelled(anyLong(), anyString());
    }

    @Test
    void cancel_idempotentWhenAlreadyCancelled() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.CANCELLED));
        OrderVO vo = service.cancel(1L, 50L);
        assertThat(vo.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderMapper, never()).markCancelled(anyLong(), anyString());
    }

    @Test
    void cancel_ok_unlocksGoodsInSameTransaction() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PENDING_PAY));
        when(orderMapper.markCancelled(eq(50L), anyString())).thenReturn(1);
        when(orderMapper.unlockGoods(eq(10L), anyLong())).thenReturn(1);

        OrderVO vo = service.cancel(1L, 50L);

        assertThat(vo.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderMapper).markCancelled(eq(50L), eq("买家主动取消"));
        verify(orderMapper).unlockGoods(eq(10L), anyLong());    // 02§2.6.6 同事务回滚
    }

    @Test
    void cancel_unlockRace_triggersConsistencyGuard() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PENDING_PAY));
        when(orderMapper.markCancelled(eq(50L), anyString())).thenReturn(1);
        when(orderMapper.unlockGoods(eq(10L), anyLong())).thenReturn(0);    // 商品已非 LOCKED

        assertThatThrownBy(() -> service.cancel(1L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOODS_STATUS_INVALID);     // 抛异常 → 事务回滚（含 markCancelled）
    }

    // ---- FR-ORDER-05 确认完成（TC-26 单元分支）----

    @Test
    void complete_rejectsPendingPay_B0304() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PENDING_PAY));
        assertThatThrownBy(() -> service.complete(1L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        verify(orderMapper, never()).markCompleted(anyLong(), anyLong());
    }

    @Test
    void complete_ok_marksGoodsSold() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PAID));
        when(orderMapper.markCompleted(eq(50L), anyLong())).thenReturn(1);
        when(orderMapper.markSold(eq(10L), anyLong())).thenReturn(1);

        OrderVO vo = service.complete(1L, 50L);

        assertThat(vo.status()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderMapper).markCompleted(eq(50L), anyLong());
        verify(orderMapper).markSold(eq(10L), anyLong());       // 02§2.6.6 同事务
    }

    @Test
    void complete_idempotentWhenAlreadyCompleted() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.COMPLETED));
        OrderVO vo = service.complete(1L, 50L);
        assertThat(vo.status()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderMapper, never()).markCompleted(anyLong(), anyLong());
    }

    @Test
    void complete_soldRace_triggersConsistencyGuard() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PAID));
        when(orderMapper.markCompleted(eq(50L), anyLong())).thenReturn(1);
        when(orderMapper.markSold(eq(10L), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.complete(1L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOODS_STATUS_INVALID);
    }

    // ---- 状态机守卫回归：取消/完成仅买方可触发，卖方调用被拒 ----

    @Test
    void cancel_bySeller_forbidden() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PENDING_PAY));
        assertThatThrownBy(() -> service.cancel(2L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.complete(2L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ---- 防御性断言：markCompleted 写入 complete_time ----

    @Test
    void complete_writesCompleteTime() {
        Order paid = order(OrderStatus.PAID);
        when(orderMapper.selectById(50L)).thenReturn(paid);
        when(orderMapper.markCompleted(eq(50L), anyLong())).thenReturn(1);
        when(orderMapper.markSold(eq(10L), anyLong())).thenReturn(1);

        service.complete(1L, 50L);

        ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(orderMapper).markCompleted(eq(50L), timeCaptor.capture());
        assertThat(paid.getCompleteTime()).isEqualTo(timeCaptor.getValue());
    }
}