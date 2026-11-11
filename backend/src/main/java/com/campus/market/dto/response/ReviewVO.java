package com.campus.market.dto.response;

import com.campus.market.entity.Review;
import com.campus.market.enums.ReviewRole;

/**
 * 评价视图（契约 API-26）：含评价人昵称（Service 关联填充）。
 */
public record ReviewVO(
        Long id,
        Long orderId,
        Long reviewerId,
        String reviewerNickname,
        ReviewRole role,
        Integer rating,
        String content,
        Long createdAt
) {
    public static ReviewVO from(Review r, String reviewerNickname) {
        return new ReviewVO(r.getId(), r.getOrderId(), r.getReviewerId(), reviewerNickname,
                r.getRole(), r.getRating(), r.getContent(), r.getCreatedAt());
    }
}