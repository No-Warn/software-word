package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.request.ReviewCreateRequest;
import com.campus.market.dto.response.ReviewVO;

/**
 * 评价服务（FR-REVIEW-01/02；契约 API-25/26）。
 */
public interface ReviewService {

    /** 交易互评（FR-REVIEW-01，BR-07）：仅 COMPLETED 订单双方、每订单每人 1 次 */
    ReviewVO create(Long reviewerId, ReviewCreateRequest req);

    /** 订单评价查询（API-26） */
    PageResult<ReviewVO> listByOrder(Long orderId, Integer page, Integer size);

    /**
     * 卖家信用分（FR-REVIEW-02，01§1.7.3）：作为收件方（reviewee）的全部评分算术平均，
     * 保留 1 位小数；无记录返回 null（前端展示"暂无评价"）。
     */
    Double creditOf(Long userId);
}