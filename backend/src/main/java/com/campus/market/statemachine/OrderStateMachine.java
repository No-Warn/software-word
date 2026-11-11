package com.campus.market.statemachine;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.enums.OrderEvent;
import com.campus.market.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单状态机：迁移表实现，迁移分支严格对齐 01§1.5.3。
 * 非法迁移抛 B0304（幂等场景由 Service 在调用前判定并返回当前态，02§2.5.1）。
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TRANSITIONS = Map.of(
            // 待支付：支付 / 取消 / 超时 / 仲裁取消
            OrderStatus.PENDING_PAY, Map.of(
                    OrderEvent.PAY, OrderStatus.PAID,
                    OrderEvent.CANCEL, OrderStatus.CANCELLED,
                    OrderEvent.TIMEOUT, OrderStatus.CLOSED,
                    OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED),
            // 交易中（已支付）：完成 / 仲裁取消
            OrderStatus.PAID, Map.of(
                    OrderEvent.COMPLETE, OrderStatus.COMPLETED,
                    OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED),
            // 终态
            OrderStatus.COMPLETED, Map.of(),
            OrderStatus.CANCELLED, Map.of(),
            OrderStatus.CLOSED, Map.of()
    );

    /**
     * 迁移合法性校验；非法迁移抛 B0304。
     */
    public void check(OrderStatus from, OrderEvent event) {
        if (target(from, event) == null) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID,
                    "订单状态[" + from + "]不允许操作[" + event + "]");
        }
    }

    /**
     * 目标状态；非法迁移返回 null。
     */
    public OrderStatus target(OrderStatus from, OrderEvent event) {
        Map<OrderEvent, OrderStatus> byEvent = TRANSITIONS.get(from);
        return byEvent == null ? null : byEvent.get(event);
    }
}