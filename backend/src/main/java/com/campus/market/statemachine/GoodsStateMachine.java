package com.campus.market.statemachine;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.enums.GoodsEvent;
import com.campus.market.enums.GoodsStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 商品状态机（BR-02）：迁移表实现，迁移分支严格对齐 01§1.5.2。
 * 非法迁移抛 B0201；写库统一使用带状态条件的 UPDATE（02§2.6.5），本类为前置守卫。
 */
@Component
public class GoodsStateMachine {

    private static final Map<GoodsStatus, Map<GoodsEvent, GoodsStatus>> TRANSITIONS = Map.ofEntries(
            // 草稿：提交审核 / 删除
            Map.entry(GoodsStatus.DRAFT, Map.of(
                    GoodsEvent.SUBMIT, GoodsStatus.PENDING_AUDIT)),
            // 待审核：通过 / 驳回
            Map.entry(GoodsStatus.PENDING_AUDIT, Map.of(
                    GoodsEvent.APPROVE, GoodsStatus.ON_SALE,
                    GoodsEvent.REJECT, GoodsStatus.REJECTED)),
            // 审核不通过：修改后重新提交
            Map.entry(GoodsStatus.REJECTED, Map.of(
                    GoodsEvent.SUBMIT, GoodsStatus.PENDING_AUDIT)),
            // 在售：被下单锁定 / 下架 / 编辑关键字段回退审核
            Map.entry(GoodsStatus.ON_SALE, Map.of(
                    GoodsEvent.LOCK, GoodsStatus.LOCKED,
                    GoodsEvent.OFF_SHELF, GoodsStatus.OFF_SHELF,
                    GoodsEvent.EDIT_REVISE, GoodsStatus.PENDING_AUDIT)),
            // 交易中：回滚在售 / 标记售出
            Map.entry(GoodsStatus.LOCKED, Map.of(
                    GoodsEvent.UNLOCK, GoodsStatus.ON_SALE,
                    GoodsEvent.MARK_SOLD, GoodsStatus.SOLD)),
            // 已售出：终态
            Map.entry(GoodsStatus.SOLD, Map.of()),
            // 已下架：重新上架（需再审核 BR-05）/ 编辑回退审核
            Map.entry(GoodsStatus.OFF_SHELF, Map.of(
                    GoodsEvent.SUBMIT, GoodsStatus.PENDING_AUDIT,
                    GoodsEvent.EDIT_REVISE, GoodsStatus.PENDING_AUDIT))
    );

    /**
     * 迁移合法性校验；非法迁移抛 B0201（BR-02）。
     */
    public void check(GoodsStatus from, GoodsEvent event) {
        if (target(from, event) == null) {
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID,
                    "商品状态[" + from + "]不允许操作[" + event + "]");
        }
    }

    /**
     * 目标状态；非法迁移返回 null。
     */
    public GoodsStatus target(GoodsStatus from, GoodsEvent event) {
        Map<GoodsEvent, GoodsStatus> byEvent = TRANSITIONS.get(from);
        return byEvent == null ? null : byEvent.get(event);
    }
}