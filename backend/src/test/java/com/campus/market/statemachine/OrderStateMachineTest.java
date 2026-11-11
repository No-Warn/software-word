package com.campus.market.statemachine;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.enums.OrderEvent;
import com.campus.market.enums.OrderStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单状态机迁移表全分支测试（01§1.5.3；TC-03/04/05/06 的状态迁移基础）。
 */
class OrderStateMachineTest {

    private final OrderStateMachine sm = new OrderStateMachine();

    @ParameterizedTest(name = "[{0}] --{1}--> [{2}] 合法")
    @CsvSource({
            "PENDING_PAY, PAY, PAID",
            "PENDING_PAY, CANCEL, CANCELLED",
            "PENDING_PAY, TIMEOUT, CLOSED",
            "PENDING_PAY, ADMIN_CANCEL, CANCELLED",
            "PAID, COMPLETE, COMPLETED",
            "PAID, ADMIN_CANCEL, CANCELLED"
    })
    void legalTransition(OrderStatus from, OrderEvent event, OrderStatus to) {
        assertThat(sm.target(from, event)).isEqualTo(to);
        assertThatCode(() -> sm.check(from, event)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "非法迁移 [{0}] --{1}--> 抛 B0304")
    @CsvSource({
            "PAID, PAY",
            "PAID, TIMEOUT",
            "COMPLETED, COMPLETE",
            "CANCELLED, CANCEL",
            "CLOSED, PAY",
            "PENDING_PAY, COMPLETE"
    })
    void illegalTransitionThrowsB0304(OrderStatus from, OrderEvent event) {
        assertThatThrownBy(() -> sm.check(from, event))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
    }

    @org.junit.jupiter.api.Test
    void terminalStatesHaveNoTransitions() {
        for (OrderEvent e : OrderEvent.values()) {
            assertThat(sm.target(OrderStatus.COMPLETED, e)).as("COMPLETED 事件 %s", e).isNull();
            assertThat(sm.target(OrderStatus.CANCELLED, e)).as("CANCELLED 事件 %s", e).isNull();
            assertThat(sm.target(OrderStatus.CLOSED, e)).as("CLOSED 事件 %s", e).isNull();
        }
    }
}