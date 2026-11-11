package com.campus.market.enums;

/**
 * 商品状态（唯一口径：需求 01§1.7.2，状态机 01§1.5.2）。
 */
public enum GoodsStatus {
    DRAFT,
    PENDING_AUDIT,
    REJECTED,
    ON_SALE,
    LOCKED,
    SOLD,
    OFF_SHELF
}