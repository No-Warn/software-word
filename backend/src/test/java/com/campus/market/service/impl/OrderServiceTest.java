package com.campus.market.service.impl;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.config.MarketProperties;
import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Order;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.OrderStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.statemachine.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

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
 * 订单服务单元测试（FR-ORDER-01/02、BR-03/04/05/06/12；TC-03/TC-04 单元分支）。
 * 并发互斥的端到端证据见 OrderConcurrencyIT（TC-03）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

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

    private Goods goods(GoodsStatus status) {
        Goods g = new Goods();
        g.setId(10L);
        g.setSellerId(2L);
        g.setTitle("考研教材");
        g.setPrice(9.9);
        g.setStatus(status);
        g.setDeleted(0);
        return g;
    }

    private Order order(OrderStatus status, Long timeoutAt) {
        Order o = new Order();
        o.setId(50L);
        o.setOrderNo("ORD20261111120000123456");
        o.setGoodsId(10L);
        o.setBuyerId(1L);
        o.setSellerId(2L);
        o.setAmount(9.9);
        o.setStatus(status);
        o.setTimeoutAt(timeoutAt);
        o.setCreatedAt(System.currentTimeMillis() - 1000);
        return o;
    }

    // ---- FR-ORDER-01 下单 ----

    @Test
    void create_rejectsSelfPurchase_B0303() {
        Goods g = goods(GoodsStatus.ON_SALE);
        g.setSellerId(1L);                      // 买家=卖家
        when(goodsMapper.selectById(10L)).thenReturn(g);

        assertThatThrownBy(() -> service.createOrder(1L, new OrderCreateRequest(10L)))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELF_PURCHASE);
    }

    @Test
    void create_rejectsWhenLockFails_B0302() {
        when(goodsMapper.selectById(10L)).thenReturn(goods(GoodsStatus.ON_SALE));
        when(orderMapper.tryLockGoods(eq(10L), anyLong())).thenReturn(0);   // 已被锁定

        assertThatThrownBy(() -> service.createOrder(1L, new OrderCreateRequest(10L)))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOODS_LOCKED);
        verify(orderMapper, never()).insert(any(Order.class));              // 锁失败绝不建单
    }

    @Test
    void create_ok_snapshotPriceAndTimeout_BR12_BR03() {
        when(goodsMapper.selectById(10L)).thenReturn(goods(GoodsStatus.ON_SALE));
        when(orderMapper.tryLockGoods(eq(10L), anyLong())).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        long before = System.currentTimeMillis();
        OrderVO vo = service.createOrder(1L, new OrderCreateRequest(10L));
        long after = System.currentTimeMillis();

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAY);
        assertThat(saved.getAmount()).isEqualTo(9.9);                       // BR-12 价格快照
        // BR-03：timeout_at = 创建时刻 + 15 分钟（± 秒级容差）
        assertThat(saved.getTimeoutAt()).isBetween(
                saved.getCreatedAt() + Duration.ofMinutes(15).toMillis() - 1000,
                saved.getCreatedAt() + Duration.ofMinutes(15).toMillis() + 1000);
        assertThat(saved.getOrderNo()).startsWith("ORD");
        assertThat(vo.amount()).isEqualTo(9.9);
        assertThat(before).isLessThanOrEqualTo(saved.getCreatedAt());
        assertThat(after).isGreaterThanOrEqualTo(saved.getCreatedAt());
    }

    // ---- FR-ORDER-02 模拟支付 ----

    @Test
    void pay_byNonBuyer_forbidden() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.PENDING_PAY, null));
        assertThatThrownBy(() -> service.pay(99L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void pay_idempotentWhenAlreadyPaid() {
        Order paid = order(OrderStatus.PAID, null);
        paid.setPayVoucherNo("PAY202611111200001234");
        when(orderMapper.selectById(50L)).thenReturn(paid);

        OrderVO vo = service.pay(1L, 50L);

        assertThat(vo.status()).isEqualTo(OrderStatus.PAID);
        assertThat(vo.payVoucherNo()).isEqualTo("PAY202611111200001234");   // 凭证唯一
        verify(orderMapper, never()).markPaid(anyLong(), anyString(), anyLong());
    }

    @Test
    void pay_rejectsWrongStatus_B0304() {
        when(orderMapper.selectById(50L)).thenReturn(order(OrderStatus.CANCELLED, null));
        assertThatThrownBy(() -> service.pay(1L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
    }

    @Test
    void pay_rejectsWhenTimeout_B0305() {
        when(orderMapper.selectById(50L))
                .thenReturn(order(OrderStatus.PENDING_PAY, System.currentTimeMillis() - 1));
        assertThatThrownBy(() -> service.pay(1L, 50L))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_TIMEOUT);
    }

    @Test
    void pay_rejectsWhenGoodsNotLocked() {
        when(orderMapper.selectById(50L))
                .thenReturn(order(OrderStatus.PENDING_PAY, System.currentTimeMillis() + 60000));
        when(goodsMapper.selectById(10L)).thenReturn(goods(GoodsStatus.ON_SALE));   // 治理异常态
        assertThatThrownBy(() -> service.pay(1L, 50L))
                .hasMessageContaining("商品状态异常");
    }

    @Test
    void pay_ok_generatesVoucher_BR04() {
        when(orderMapper.selectById(50L))
                .thenReturn(order(OrderStatus.PENDING_PAY, System.currentTimeMillis() + 60000));
        when(goodsMapper.selectById(10L)).thenReturn(goods(GoodsStatus.LOCKED));
        when(orderMapper.markPaid(eq(50L), anyString(), anyLong())).thenReturn(1);

        OrderVO vo = service.pay(1L, 50L);

        // BR-04：PAY + 14 位时间戳 + 4 位随机 = PAY + 18 位数字
        assertThat(vo.payVoucherNo()).matches("PAY\\d{18}");
        verify(orderMapper).markPaid(eq(50L), eq(vo.payVoucherNo()), anyLong());
    }

    @Test
    void pay_raceFallback_idempotentOrError() {
        Order pending = order(OrderStatus.PENDING_PAY, System.currentTimeMillis() + 60000);
        when(orderMapper.selectById(50L)).thenReturn(pending);
        when(goodsMapper.selectById(10L)).thenReturn(goods(GoodsStatus.LOCKED));
        when(orderMapper.markPaid(eq(50L), anyString(), anyLong())).thenReturn(0);  // 竞态：行数=0
        // 重查已是 PAID（他人先完成）→ 幂等返回
        Order latest = order(OrderStatus.PAID, null);
        latest.setPayVoucherNo("PAY202611111200009999");
        when(orderMapper.selectById(50L)).thenReturn(pending, latest);

        OrderVO vo = service.pay(1L, 50L);
        assertThat(vo.status()).isEqualTo(OrderStatus.PAID);
    }
}