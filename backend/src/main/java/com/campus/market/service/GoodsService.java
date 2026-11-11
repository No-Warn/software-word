package com.campus.market.service;

import com.campus.market.dto.request.GoodsUpsertRequest;
import com.campus.market.dto.response.GoodsVO;

/**
 * 商品写服务（FR-GOODS-01~04；契约 API-10~13）。
 */
public interface GoodsService {

    /** 发布商品 → PENDING_AUDIT（FR-GOODS-01） */
    GoodsVO publish(Long sellerId, GoodsUpsertRequest req);

    /** 编辑商品：LOCKED/SOLD 拒绝；关键字段变更回退审核（FR-GOODS-02） */
    GoodsVO edit(Long userId, Long goodsId, GoodsUpsertRequest req);

    /** 上架（需再审核 BR-05）/ 下架（FR-GOODS-03） */
    void shelf(Long userId, Long goodsId, boolean onShelf);

    /** 删除：仅 DRAFT/OFF_SHELF/REJECTED（FR-GOODS-04，逻辑删除） */
    void delete(Long userId, Long goodsId);
}