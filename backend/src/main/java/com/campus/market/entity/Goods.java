package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.market.enums.GoodsStatus;
import lombok.Data;

/**
 * 商品实体（表结构 02§2.4.2 goods）。状态机 BR-02 由 GoodsStateMachine 守卫 + 条件更新落地。
 */
@Data
@TableName("goods")
public class Goods {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sellerId;

    private Long categoryId;

    private String title;

    private String description;

    /** 成色：NEW/LIKE_NEW/GOOD/WELL_USED（schema CHECK 约束） */
    private String condition;

    /** 价格（BR-09：0.01~99999.99） */
    private Double price;

    /** 图片 URL 列表 JSON 数组（BR-10：1~9 张） */
    private String imagesJson;

    private GoodsStatus status;

    /** 审核驳回原因（FR-ADMIN-01 驳回必填） */
    private String rejectReason;

    private Long publishedAt;

    /** 逻辑删除（FR-GOODS-04） */
    @TableLogic
    private Integer deleted;

    private Long createdAt;

    private Long updatedAt;
}