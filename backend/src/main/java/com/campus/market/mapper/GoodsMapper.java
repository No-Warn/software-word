package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Goods;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper（02§2.3.1）。状态流转一律走带状态条件的 UPDATE（乐观锁语义，02§2.6.5）；
 * 下单互斥 tryLockGoods/unlockGoods/markSold 属 T-09 交付。
 */
public interface GoodsMapper extends BaseMapper<Goods> {

    /**
     * 通用状态流转：仅当当前状态等于期望值时更新（影响行数=0 表示竞态失败）。
     */
    @Update("UPDATE goods SET status = #{to}, updated_at = #{now} "
            + "WHERE id = #{goodsId} AND status = #{from} AND deleted = 0")
    int transitionStatus(Long goodsId, String from, String to, Long now);
}