package com.campus.market.controller;

import com.campus.market.common.PageResult;
import com.campus.market.common.Result;
import com.campus.market.dto.request.ReviewCreateRequest;
import com.campus.market.dto.response.ReviewVO;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价接口（契约 API-25/26，02§2.5.2）。
 * 评价查询（GET /orders/{id}/reviews）游客可见（SecurityConfig GET 放行，FR-REVIEW-02 信用分展示）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/reviews")
    public Result<ReviewVO> create(@Valid @RequestBody ReviewCreateRequest req) {
        return Result.ok(reviewService.create(SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/orders/{id}/reviews")
    public Result<PageResult<ReviewVO>> listByOrder(@PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(reviewService.listByOrder(id, page, size));
    }
}