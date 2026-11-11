package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.market.enums.ReviewRole;
import lombok.Data;

/**
 * 评价实体（表结构 02§4.2 review）。BR-07：仅 COMPLETED 订单双方可评、每订单每人 1 次
 * （UNIQUE(order_id, reviewer_id) 兜底）。
 */
@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long reviewerId;

    private Long revieweeId;

    /** BUYER_TO_SELLER / SELLER_TO_BUYER（CH-008 口径） */
    private ReviewRole role;

    /** 1~5 星（schema CHECK） */
    private Integer rating;

    private String content;

    private Long createdAt;
}