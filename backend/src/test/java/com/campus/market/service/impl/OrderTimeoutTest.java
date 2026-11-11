package com.campus.market.service.impl;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.config.MarketProperties;
import com.campus.market.entity.Order;
import com.campus.market.enums.OrderStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.statemachine.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
 * 超时关闭测试（FR-ORDER-04、BR-03；TC-05/TC-06 单元分支；02§2.6.3 逐单事务）。
 * 时间加速口径：直接把订单 timeout_at 置为过去（压缩 15 分钟窗口），无需时间框架。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderTimeoutTest {

    @Mock
    OrderMapper orderMapper;
    @Mock
    GoodsMapper goodsMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    PlatformTransactionManager transactionManager;

    OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        service = new OrderServiceImpl(orderMapper, goodsMapper, userMapper,
                new OrderStateMachine(), new MarketProperties(), tt);
    }

    private Order timeoutOrder(long id, long goodsId) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("ORD20261111120000123456");
        o.setGoodsId(goodsId);
        o.setBuyerId(1L);
        o.setSellerId(2L);
        o.setAmount(9.9);
        o.setStatus(OrderStatus.PENDING_PAY);
        o.setTimeoutAt(System.currentTimeMillis() - 1000);      // 已超时
        o.setCreatedAt(System.currentTimeMillis() - 90000);
        return o;
    }

    // TC-05 单元分支：超时关闭 + 商品回滚
    @Test
    void closeTimeout_closesAndUnlocks() {
        when(orderMapper.selectList(any())).thenReturn(List.of(timeoutOrder(50L, 10L)));
        when(orderMapper.markClosed(50L)).thenReturn(1);
        when(orderMapper.unlockGoods(eq(10L), anyLong())).thenReturn(1);

        int closed = service.closeTimeoutOrders();

        assertThat(closed).isEqualTo(1);
        verify(orderMapper).markClosed(50L);
        verify(orderMapper).unlockGoods(eq(10L), anyLong());
    }

    // TC-06 单元分支：先到先得——订单已被支付/取消时 markClosed 行数=0，跳过且不回滚商品
    @Test
    void closeTimeout_skipsWhenAlreadySettled() {
        when(orderMapper.selectList(any())).thenReturn(List.of(timeoutOrder(50L, 10L)));
        when(orderMapper.markClosed(50L)).thenReturn(0);

        int closed = service.closeTimeoutOrders();

        assertThat(closed).isZero();
        verify(orderMapper, never()).unlockGoods(anyLong(), anyLong());
    }

    // 一致性兜底：unlock 失败 → 本单事务回滚（BizException 被逐单隔离捕获，02§2.6.3 不中断其他单）
    @Test
    void closeTimeout_unlockFailure_skippedWithoutPropagating() {
        when(orderMapper.selectList(any())).thenReturn(List.of(timeoutOrder(50L, 10L)));
        when(orderMapper.markClosed(50L)).thenReturn(1);
        when(orderMapper.unlockGoods(eq(10L), anyLong())).thenReturn(0);

        // 单笔一致性异常被隔离：不向上传播、不中断循环；该单本轮未关闭，下轮重试
        int closed = service.closeTimeoutOrders();

        assertThat(closed).isZero();
        verify(orderMapper).markClosed(50L);
        verify(orderMapper).unlockGoods(eq(10L), anyLong());
    }

    // 02§2.6.3 逐单语义：一单失败不影响其他单（异常被捕获，循环继续）
    @Test
    void closeTimeout_singleFailureDoesNotBlockOthers() {
        when(orderMapper.selectList(any()))
                .thenReturn(List.of(timeoutOrder(50L, 10L), timeoutOrder(51L, 11L)));
        when(orderMapper.markClosed(50L)).thenReturn(1);
        when(orderMapper.unlockGoods(eq(10L), anyLong())).thenReturn(0);    // 第一单失败
        when(orderMapper.markClosed(51L)).thenReturn(1);
        when(orderMapper.unlockGoods(eq(11L), anyLong())).thenReturn(1);    // 第二单成功

        int closed = service.closeTimeoutOrders();

        assertThat(closed).isEqualTo(1);
        verify(orderMapper).markClosed(51L);
    }
}