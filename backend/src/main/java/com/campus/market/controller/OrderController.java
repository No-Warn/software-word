package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单交易接口（契约 API-17/18，02§2.5.2）。
 * 取消/完成/我的订单（API-19~21）归 T-10 增量交付。
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest req) {
        return Result.ok(orderService.createOrder(SecurityUtils.currentUserId(), req));
    }

    @PostMapping("/{id}/pay")
    public Result<OrderVO> pay(@PathVariable Long id) {
        return Result.ok(orderService.pay(SecurityUtils.currentUserId(), id));
    }
}