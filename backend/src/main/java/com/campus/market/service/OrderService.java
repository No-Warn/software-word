package com.campus.market.service;

import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;

/**
 * 订单服务（FR-ORDER-01/02；契约 API-17/18）。
 * 取消/完成/我的订单（API-19~21）归 T-10；超时关闭归 T-11。
 */
public interface OrderService {

    /**
     * 下单（FR-ORDER-01）：事务内互斥锁定商品 + 创建待支付订单（BR-05/06/12）。
     */
    OrderVO createOrder(Long buyerId, OrderCreateRequest req);

    /**
     * 模拟支付（FR-ORDER-02）：幂等；生成虚拟凭证（BR-04）；超时/治理态拒绝。
     */
    OrderVO pay(Long buyerId, Long orderId);
}