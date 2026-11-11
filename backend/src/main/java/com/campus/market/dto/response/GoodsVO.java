package com.campus.market.dto.response;

import com.campus.market.entity.Goods;
import com.campus.market.enums.GoodsStatus;

import java.util.List;

/**
 * 商品视图（发布/编辑返回；列表/详情复用，卖家昵称与分类名由读服务 T-07 关联填充）。
 */
public record GoodsVO(
        Long id,
        Long sellerId,
        String sellerNickname,
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
    public static GoodsVO from(Goods g, List<String> images, String categoryName) {
        return new GoodsVO(g.getId(), g.getSellerId(), null, g.getCategoryId(), categoryName,
                g.getTitle(), g.getDescription(), g.getCondition(), g.getPrice(),
                images, g.getStatus(), g.getRejectReason(), g.getPublishedAt());
    }
}