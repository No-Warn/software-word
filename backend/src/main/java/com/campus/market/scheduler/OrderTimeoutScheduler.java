package com.campus.market.scheduler;

import com.campus.market.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时订单关闭调度器（FR-ORDER-04 / BR-03，02§2.6.3）：
 * 每 60 秒扫描一次（BR-03"每分钟"口径），薄壳设计——只调 Service，不直写 DB（02§2.2.2）。
 * 关闭逻辑（逐单事务 + 条件更新先到先得）在 OrderServiceImpl.closeTimeoutOrders。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void closeTimeoutOrders() {
        try {
            int closed = orderService.closeTimeoutOrders();
            if (closed > 0) {
                log.info("timeout orders closed: {}", closed);
            }
        } catch (Exception e) {
            // 调度器自身不得因单轮失败中断后续调度
            log.error("close timeout orders failed", e);
        }
    }
}