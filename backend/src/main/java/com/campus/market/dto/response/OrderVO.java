package com.campus.market.dto.response;

import com.campus.market.entity.Order;
import com.campus.market.enums.OrderStatus;

/**
 * 订单视图（契约 API-17/18/21）。counterpartNickname：对方昵称（"我的订单"列表展示用，T-10）。
 */
public record OrderVO(
        Long id,
        String orderNo,
        Long goodsId,
        String goodsTitle,
        Long buyerId,
        Long sellerId,
        String counterpartNickname,
        Double amount,
        OrderStatus status,
        String payVoucherNo,
        Long payTime,
        Long completeTime,
        Long timeoutAt,
        Long createdAt
) {
    public static OrderVO from(Order o, String goodsTitle) {
        return from(o, goodsTitle, null);
    }

    public static OrderVO from(Order o, String goodsTitle, String counterpartNickname) {
        return new OrderVO(o.getId(), o.getOrderNo(), o.getGoodsId(), goodsTitle,
                o.getBuyerId(), o.getSellerId(), counterpartNickname,
                o.getAmount(), o.getStatus(), o.getPayVoucherNo(), o.getPayTime(),
                o.getCompleteTime(), o.getTimeoutAt(), o.getCreatedAt());
    }
}