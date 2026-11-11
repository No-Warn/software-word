package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.common.PageResult;
import com.campus.market.config.MarketProperties;
import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Order;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.OrderEvent;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.TradeRole;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.OrderService;
import com.campus.market.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务（FR-ORDER-01~06；API-17~21 + FR-ORDER-04）。
 * 下单互斥（BR-06，02§2.6.1）：事务内 tryLockGoods 条件更新（行数=1 才建单）；
 * SQLite 写串行化 + WAL 兜底并发（DataConfig）；INSERT 失败事务整体回滚，商品自动回到 ON_SALE。
 * 价格快照（BR-12）：amount 取下单时商品价，后续改价不影响。
 * 模拟支付（BR-04，02§2.6.2）：幂等（重复支付返回当前态）→ 状态机守卫（B0304）→
 * 超时判定（B0305）→ 商品治理态拒绝 → markPaid 条件更新（竞态后到者按幂等/错误处理）。
 * 取消/完成（02§2.6.6）：订单变更与商品回滚/售出在**同一事务**，任一失败整体回滚。
 * 超时关闭（BR-03，02§2.6.3）：逐单编程式事务（TransactionTemplate），
 * markClosed 条件更新先到先得，行数=0 跳过；unlock 失败抛异常回滚本单（不影响其他单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final OrderStateMachine orderStateMachine;
    private final MarketProperties props;
    private final TransactionTemplate transactionTemplate;

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

    @Override
    @Transactional
    public OrderVO cancel(Long buyerId, Long orderId) {
        Order order = requireBuyerOrder(buyerId, orderId);
        long now = System.currentTimeMillis();
        // 幂等（02§2.5.1）：已取消直接返回当前态
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderVO.from(order, null);
        }
        // 仅 PENDING_PAY 可取消（PAID 只能仲裁取消，01§1.5.3）
        orderStateMachine.check(order.getStatus(), OrderEvent.CANCEL);  // 否则 B0304
        int rows = orderMapper.markCancelled(orderId, "买家主动取消");
        if (rows != 1) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);     // 竞态兜底
        }
        // 02§2.6.6：同事务回滚商品在售（BR-02：LOCKED → ON_SALE）
        int unlocked = orderMapper.unlockGoods(order.getGoodsId(), now);
        if (unlocked != 1) {
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID);     // 一致性兜底 → 事务整体回滚
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("买家主动取消");
        return OrderVO.from(order, null);
    }

    @Override
    @Transactional
    public OrderVO complete(Long buyerId, Long orderId) {
        Order order = requireBuyerOrder(buyerId, orderId);
        long now = System.currentTimeMillis();
        // 幂等：已完成直接返回当前态
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return OrderVO.from(order, null);
        }
        orderStateMachine.check(order.getStatus(), OrderEvent.COMPLETE);    // 仅 PAID 可完成
        int rows = orderMapper.markCompleted(orderId, now);
        if (rows != 1) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }
        // 02§2.6.6：同事务标记商品 SOLD
        int sold = orderMapper.markSold(order.getGoodsId(), now);
        if (sold != 1) {
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID);     // 一致性兜底 → 事务整体回滚
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompleteTime(now);
        return OrderVO.from(order, null);
    }

    @Override
    public PageResult<OrderVO> listMine(Long userId, TradeRole role, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);  // 02§2.5.1
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        boolean asSeller = role == TradeRole.SELLER;
        if (asSeller) {
            qw.eq(Order::getSellerId, userId);
        } else {
            qw.eq(Order::getBuyerId, userId);
        }
        qw.orderByDesc(Order::getCreatedAt);
        Page<Order> result = orderMapper.selectPage(new Page<>(p, s), qw);
        List<Order> orders = result.getRecords();

        // 批量关联商品标题与对方昵称（避免 N+1）
        Set<Long> goodsIds = orders.stream().map(Order::getGoodsId).collect(Collectors.toSet());
        Set<Long> counterpartIds = orders.stream()
                .map(o -> asSeller ? o.getBuyerId() : o.getSellerId())
                .collect(Collectors.toSet());
        Map<Long, String> titles = goodsIds.isEmpty() ? Map.of()
                : goodsMapper.selectBatchIds(goodsIds).stream()
                        .collect(Collectors.toMap(Goods::getId, Goods::getTitle));
        Map<Long, String> nicknames = counterpartIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(counterpartIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));

        List<OrderVO> vos = orders.stream()
                .map(o -> OrderVO.from(o, titles.get(o.getGoodsId()),
                        nicknames.get(asSeller ? o.getBuyerId() : o.getSellerId())))
                .toList();
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    @Override
    public int closeTimeoutOrders() {
        long now = System.currentTimeMillis();
        List<Order> timeoutOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAY)
                .lt(Order::getTimeoutAt, now));
        int closed = 0;
        for (Order order : timeoutOrders) {
            // 02§2.6.3：逐单编程式事务（避免 Spring 自调用事务失效），一单失败不影响其他单
            try {
                Boolean ok = transactionTemplate.execute(tx -> {
                    // 条件更新先到先得：行数=0 表示已被支付/取消（TC-06 竞态防护点）
                    int rows = orderMapper.markClosed(order.getId());
                    if (rows != 1) {
                        return false;
                    }
                    int unlocked = orderMapper.unlockGoods(order.getGoodsId(), System.currentTimeMillis());
                    if (unlocked != 1) {
                        throw new BizException(ErrorCode.GOODS_STATUS_INVALID);   // 回滚本单事务
                    }
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) {
                    closed++;
                } else {
                    log.info("timeout order skipped (already settled): id={}", order.getId());
                }
            } catch (Exception e) {
                // 单笔失败（如商品一致性异常）仅回滚本单事务，记录后继续处理其他订单，下轮重试
                log.error("close timeout order failed: id={}", order.getId(), e);
            }
        }
        return closed;
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