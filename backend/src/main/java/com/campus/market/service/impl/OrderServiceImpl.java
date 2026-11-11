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
import com.campus.market.service.OrderService;
import com.campus.market.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务（FR-ORDER-01/02；API-17/18）。
 * 下单互斥（BR-06，02§2.6.1）：事务内 tryLockGoods 条件更新（行数=1 才建单）；
 * SQLite 写串行化 + WAL 兜底并发（DataConfig）；INSERT 失败事务整体回滚，商品自动回到 ON_SALE。
 * 价格快照（BR-12）：amount 取下单时商品价，后续改价不影响。
 * 模拟支付（BR-04，02§2.6.2）：幂等（重复支付返回当前态）→ 状态机守卫（B0304）→
 * 超时判定（B0305）→ 商品治理态拒绝 → markPaid 条件更新（竞态后到者按幂等/错误处理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final OrderStateMachine orderStateMachine;
    private final MarketProperties props;

    @Override
    @Transactional
    public OrderVO createOrder(Long buyerId, OrderCreateRequest req) {
        Goods goods = goodsMapper.selectById(req.goodsId());
        if (goods == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品不存在或已删除");
        }
        if (goods.getSellerId().equals(buyerId)) {
            throw new BizException(ErrorCode.SELF_PURCHASE);            // B0303
        }
        long now = System.currentTimeMillis();
        // BR-05/BR-06：互斥锁定，行数=1 才允许建单（竞态后到者 B0302）
        // 02§2.3.3：tryLockGoods 定义于 OrderMapper（订单事务的一部分），非 GoodsMapper
        int locked = orderMapper.tryLockGoods(goods.getId(), now);
        if (locked != 1) {
            throw new BizException(ErrorCode.GOODS_LOCKED);
        }
        Order order = new Order();
        order.setOrderNo(generateNo("ORD", now, 6));
        order.setGoodsId(goods.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(goods.getSellerId());
        order.setAmount(goods.getPrice());                              // BR-12 价格快照
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setTimeoutAt(now + Duration.ofMinutes(props.getPayTimeoutMinutes()).toMillis());  // BR-03
        order.setCreatedAt(now);
        orderMapper.insert(order);
        return OrderVO.from(order, goods.getTitle());
    }

    @Override
    public OrderVO pay(Long buyerId, Long orderId) {
        Order order = requireBuyerOrder(buyerId, orderId);
        long now = System.currentTimeMillis();
        // 幂等（02§2.5.1）：已支付直接返回当前态，不产生第二张凭证
        if (order.getStatus() == OrderStatus.PAID) {
            return OrderVO.from(order, null);
        }
        orderStateMachine.check(order.getStatus(), OrderEvent.PAY);     // 非待支付 → B0304
        if (order.getTimeoutAt() != null && order.getTimeoutAt() < now) {
            throw new BizException(ErrorCode.ORDER_TIMEOUT);            // B0305
        }
        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods == null || goods.getStatus() != GoodsStatus.LOCKED) {
            // 02§2.6.2：商品被治理下架/封禁则拒绝支付，引导取消
            throw new BizException(ErrorCode.GOODS_NOT_ON_SALE, "商品状态异常，请取消订单");
        }
        String voucher = generateNo("PAY", now, 4);
        int rows = orderMapper.markPaid(orderId, voucher, now);
        if (rows != 1) {
            // 竞态（恰被取消/关闭）：以库内最新状态为准给出幂等或错误
            Order latest = orderMapper.selectById(orderId);
            if (latest != null && latest.getStatus() == OrderStatus.PAID) {
                return OrderVO.from(latest, null);
            }
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }
        order.setStatus(OrderStatus.PAID);
        order.setPayVoucherNo(voucher);
        order.setPayTime(now);
        log.info("order paid: orderNo={}, voucher={}", order.getOrderNo(), voucher);
        return OrderVO.from(order, goods.getTitle());
    }

    private Order requireBuyerOrder(Long buyerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);                // NFR-SEC-02 属主
        }
        return order;
    }

    /** BR-04 口径：前缀 + yyyyMMddHHmmss + N 位随机（order_no=ORD+6位，凭证=PAY+4位） */
    private String generateNo(String prefix, long epochMilli, int randomDigits) {
        String ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault())
                .format(TS);
        int bound = (int) Math.pow(10, randomDigits);
        int rand = ThreadLocalRandom.current().nextInt(bound / 10, bound);   // 首位非零，保证位数
        return prefix + ts + rand;
    }
}