package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.response.GoodsDetailVO;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.dto.response.MyGoodsVO;

/**
 * 商品读服务（FR-GOODS-05~08；契约 API-14/15/16）。与写服务分离（读写各自聚焦）。
 */
public interface GoodsQueryService {

    /**
     * 在售商品列表/搜索（FR-GOODS-05/07）：keyword 命中标题+描述；分类/价格区间过滤；三种排序。
     */
    PageResult<GoodsVO> list(String keyword, Long categoryId, Double minPrice, Double maxPrice,
                             String sort, Integer page, Integer size);

    /**
     * 商品详情（FR-GOODS-06）：游客仅可看 ON_SALE；属主可见本人全部状态。
     */
    GoodsDetailVO detail(Long goodsId, Long viewerId);

    /**
     * 我的商品（FR-GOODS-08）：按状态分组 + 数量统计。
     */
    MyGoodsVO myGoods(Long userId);
}