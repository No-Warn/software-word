package com.campus.market.dto.response;

import com.campus.market.entity.Order;
import com.campus.market.enums.OrderStatus;

/**
 * 订单视图（契约 API-17/18/21）。goodsTitle 由 Service 关联填充（列表/详情展示用）。
 */
public record OrderVO(
        Long id,
        String orderNo,
        Long goodsId,
        String goodsTitle,
        Long buyerId,
        Long sellerId,
        Double amount,
        OrderStatus status,
        String payVoucherNo,
        Long payTime,
        Long completeTime,
        Long timeoutAt,
        Long createdAt
) {
    public static OrderVO from(Order o, String goodsTitle) {
        return new OrderVO(o.getId(), o.getOrderNo(), o.getGoodsId(), goodsTitle,
                o.getBuyerId(), o.getSellerId(), o.getAmount(), o.getStatus(),
                o.getPayVoucherNo(), o.getPayTime(), o.getCompleteTime(),
                o.getTimeoutAt(), o.getCreatedAt());
    }
}