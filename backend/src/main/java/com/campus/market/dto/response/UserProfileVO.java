package com.campus.market.dto.response;

import com.campus.market.dto.response.GoodsVO;

import java.util.List;

/**
 * 他人主页视图（契约 API-07，FR-REVIEW-02）：用户信息 + 信用分 + 在售商品。
 * 信用分无记录为 null（前端"暂无评价"）。
 */
public record UserProfileVO(
        Long id,
        String nickname,
        String avatarUrl,
        String bio,
        Double credit,
        List<GoodsVO> onSaleGoods
) {
}