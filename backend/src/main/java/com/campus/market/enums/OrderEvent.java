package com.campus.market.enums;

/**
 * 订单状态机事件（触发来源：买家操作 / 定时任务 / 管理员仲裁）。
 */
public enum OrderEvent {
    /** 模拟支付成功（FR-ORDER-02） */
    PAY,
    /** 买家主动取消（FR-ORDER-03） */
    CANCEL,
    /** 支付超时自动关闭（FR-ORDER-04，BR-03） */
    TIMEOUT,
    /** 买家确认完成·面交（FR-ORDER-05，ADR-008 单方确认） */
    COMPLETE,
    /** 管理员仲裁取消（FR-ADMIN-04，凭证作废+商品回滚） */
    ADMIN_CANCEL
}