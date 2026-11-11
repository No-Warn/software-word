package com.campus.market.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 我的商品视图（契约 API-16，FR-GOODS-08）：按状态分组 + 各状态数量。
 */
public record MyGoodsVO(Map<String, List<GoodsVO>> groups, Map<String, Long> counts) {
}