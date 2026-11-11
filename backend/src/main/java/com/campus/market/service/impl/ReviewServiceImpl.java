package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.common.PageResult;
import com.campus.market.dto.request.ReviewCreateRequest;
import com.campus.market.dto.response.ReviewVO;
import com.campus.market.entity.Order;
import com.campus.market.entity.Review;
import com.campus.market.entity.User;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.ReviewRole;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.ReviewService;
import com.campus.market.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评价服务（FR-REVIEW-01/02；BR-07）：
 * 仅 COMPLETED 订单双方可评（属主校验，NFR-SEC-02 双防线）；
 * 每订单每人 1 次（前置查重 + UNIQUE(order_id, reviewer_id) 兜底）；
 * 信用分（FR-REVIEW-02，01§1.7.3）：reviewee 全部评分算术平均保留 1 位小数，无记录返回 null。
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;

    @Override
    public ReviewVO create(Long reviewerId, ReviewCreateRequest req) {
        Order order = orderMapper.selectById(req.orderId());
        if (order == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "订单不存在");
        }
        // BR-07：仅 COMPLETED 订单可评（B0402）
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BizException(ErrorCode.REVIEW_INVALID, "仅已完成订单可评价");
        }
        // BR-07：仅订单双方可评（NFR-SEC-02 属主校验）
        boolean isBuyer = order.getBuyerId().equals(reviewerId);
        boolean isSeller = order.getSellerId().equals(reviewerId);
        if (!isBuyer && !isSeller) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        ReviewRole role = isBuyer ? ReviewRole.BUYER_TO_SELLER : ReviewRole.SELLER_TO_BUYER;
        Long revieweeId = isBuyer ? order.getSellerId() : order.getBuyerId();
        // BR-11：评价内容命中敏感词拒绝（B0401）
        if (sensitiveWordService.hits(req.content())) {
            throw new BizException(ErrorCode.SENSITIVE_WORD);       // B0401
        }
        // BR-07：每订单每人 1 次（前置查重；UNIQUE 兜底并发）
        Long duplicated = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, req.orderId())
                .eq(Review::getReviewerId, reviewerId));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ErrorCode.REVIEW_INVALID, "该订单已评价过");
        }
        Review review = new Review();
        review.setOrderId(req.orderId());
        review.setReviewerId(reviewerId);
        review.setRevieweeId(revieweeId);
        review.setRole(role);
        review.setRating(req.rating());
        review.setContent(req.content() == null ? "" : req.content());
        review.setCreatedAt(System.currentTimeMillis());
        reviewMapper.insert(review);
        return ReviewVO.from(review, null);
    }

    @Override
    public PageResult<ReviewVO> listByOrder(Long orderId, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);
        Page<Review> result = reviewMapper.selectPage(new Page<>(p, s), new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId)
                .orderByDesc(Review::getCreatedAt));
        List<Review> reviews = result.getRecords();
        Set<Long> reviewerIds = reviews.stream().map(Review::getReviewerId).collect(Collectors.toSet());
        Map<Long, String> nicknames = reviewerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(reviewerIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));
        List<ReviewVO> vos = reviews.stream()
                .map(r -> ReviewVO.from(r, nicknames.get(r.getReviewerId())))
                .toList();
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    @Override
    public Double creditOf(Long userId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getRevieweeId, userId));
        if (reviews.isEmpty()) {
            return null;                                        // "暂无评价"
        }
        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}