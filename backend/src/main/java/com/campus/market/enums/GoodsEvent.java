package com.campus.market.enums;

/**
 * 商品状态机事件（触发来源：卖家操作 / 审核动作 / 订单联动）。
 */
public enum GoodsEvent {
    /** 提交审核（发布/重新上架/修改后重新提交，均需再审核 BR-05） */
    SUBMIT,
    /** 审核通过（FR-ADMIN-01） */
    APPROVE,
    /** 审核驳回（FR-ADMIN-01，reject_reason 必填） */
    REJECT,
    /** 被下单锁定（FR-ORDER-01） */
    LOCK,
    /** 订单取消/关闭回滚在售（FR-ORDER-03/04） */
    UNLOCK,
    /** 订单完成标记售出（FR-ORDER-05） */
    MARK_SOLD,
    /** 卖家下架（FR-GOODS-03） */
    OFF_SHELF,
    /** 删除草稿/已下架商品（FR-GOODS-04，逻辑删除） */
    DELETE,
    /** 编辑关键字段回退审核（FR-GOODS-02：ON_SALE/OFF_SHELF 编辑标题/描述/图片/价格） */
    EDIT_REVISE
}