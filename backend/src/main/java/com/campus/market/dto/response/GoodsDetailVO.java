package com.campus.market.dto.response;

import com.campus.market.entity.Goods;
import com.campus.market.enums.GoodsStatus;

import java.util.List;

/**
 * 商品详情视图（契约 API-15）。
 * sellerCredit（卖家信用分）由 T-13 评价模块交付后填充；留言列表由 T-12 交付后并入 VO
 * （增量交付，G4 联调前字段齐备）。
 */
public record GoodsDetailVO(
        Long id,
        Long sellerId,
        String sellerNickname,
        Double sellerCredit,
        Long categoryId,
        String categoryName,
        String title,
        String description,
        String condition,
        Double price,
        List<String> images,
        GoodsStatus status,
        String rejectReason,
        Long publishedAt
) {
    public static GoodsDetailVO of(Goods g, List<String> images, String sellerNickname,
                                   Double sellerCredit, String categoryName) {
        return new GoodsDetailVO(g.getId(), g.getSellerId(), sellerNickname, sellerCredit,
                g.getCategoryId(), categoryName, g.getTitle(), g.getDescription(),
                g.getCondition(), g.getPrice(), images, g.getStatus(),
                g.getRejectReason(), g.getPublishedAt());
    }
}