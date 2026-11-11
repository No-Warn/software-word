package com.campus.market.enums;

/**
 * 订单状态（唯一口径：需求 01§1.7.2，状态机 01§1.5.3）。
 */
public enum OrderStatus {
    PENDING_PAY,
    PAID,
    COMPLETED,
    CANCELLED,
    CLOSED
}