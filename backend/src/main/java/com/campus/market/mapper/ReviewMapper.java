package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Review;

/**
 * 评价 Mapper（02§2.3.1）。信用分聚合（FR-REVIEW-02）由 Service 内存计算（规模可控）。
 */
public interface ReviewMapper extends BaseMapper<Review> {
}