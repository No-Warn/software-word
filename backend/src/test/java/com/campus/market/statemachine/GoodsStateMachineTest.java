package com.campus.market.statemachine;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.enums.GoodsEvent;
import com.campus.market.enums.GoodsStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BR-02 商品状态机迁移表全分支测试（对应追溯矩阵 TC-08/TC-19/TC-20 的状态迁移基础）。
 */
class GoodsStateMachineTest {

    private final GoodsStateMachine sm = new GoodsStateMachine();

    @ParameterizedTest(name = "[{0}] --{1}--> [{2}] 合法")
    @CsvSource({
            "DRAFT, SUBMIT, PENDING_AUDIT",
            "PENDING_AUDIT, APPROVE, ON_SALE",
            "PENDING_AUDIT, REJECT, REJECTED",
            "REJECTED, SUBMIT, PENDING_AUDIT",
            "ON_SALE, LOCK, LOCKED",
            "ON_SALE, OFF_SHELF, OFF_SHELF",
            "ON_SALE, EDIT_REVISE, PENDING_AUDIT",
            "LOCKED, UNLOCK, ON_SALE",
            "LOCKED, MARK_SOLD, SOLD",
            "OFF_SHELF, SUBMIT, PENDING_AUDIT",
            "OFF_SHELF, EDIT_REVISE, PENDING_AUDIT"
    })
    void legalTransition(GoodsStatus from, GoodsEvent event, GoodsStatus to) {
        assertThat(sm.target(from, event)).isEqualTo(to);
        assertThatCode(() -> sm.check(from, event)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "非法迁移 [{0}] --{1}--> 抛 B0201")
    @CsvSource({
            "DRAFT, LOCK",
            "DRAFT, APPROVE",
            "SOLD, SUBMIT",
            "REJECTED, APPROVE",
            "OFF_SHELF, LOCK",
            "ON_SALE, UNLOCK",
            "PENDING_AUDIT, OFF_SHELF"
    })
    void illegalTransitionThrowsB0201(GoodsStatus from, GoodsEvent event) {
        assertThatThrownBy(() -> sm.check(from, event))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOODS_STATUS_INVALID);
    }

    @org.junit.jupiter.api.Test
    void soldIsTerminal() {
        for (GoodsEvent e : GoodsEvent.values()) {
            assertThat(sm.target(GoodsStatus.SOLD, e)).as("SOLD 终态事件 %s", e).isNull();
        }
    }
}