package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.enums.TradeRole;

/**
 * 订单服务（FR-ORDER-01~06；契约 API-17~21 + FR-ORDER-04 超时关闭）。
 */
public interface OrderService {

    /** 下单（FR-ORDER-01）：事务内互斥锁定商品 + 创建待支付订单（BR-05/06/12） */
    OrderVO createOrder(Long buyerId, OrderCreateRequest req);

    /** 模拟支付（FR-ORDER-02）：幂等；生成虚拟凭证（BR-04）；超时/治理态拒绝 */
    OrderVO pay(Long buyerId, Long orderId);

    /** 买家取消（FR-ORDER-03）：PENDING_PAY 才可取消；商品同事务回滚在售 */
    OrderVO cancel(Long buyerId, Long orderId);

    /** 确认完成（FR-ORDER-05）：PAID → COMPLETED + 商品 SOLD（ADR-008 单方确认） */
    OrderVO complete(Long buyerId, Long orderId);

    /** 我的订单（FR-ORDER-06）：role=BUYER（我买到的）/SELLER（我卖出的） */
    PageResult<OrderVO> listMine(Long userId, TradeRole role, Integer page, Integer size);

    /**
     * 关闭全部超时待支付订单（FR-ORDER-04 / BR-03，02§2.6.3）：
     * 逐单事务，条件更新先到先得；返回本次实际关闭数。由 OrderTimeoutScheduler 每分钟调用。
     */
    int closeTimeoutOrders();
}