package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.market.enums.OrderStatus;
import lombok.Data;

/**
 * 订单实体（表结构 02§4.2 orders，表名避开保留字 order）。
 * amount 为下单时价格快照（BR-12）；timeout_at = created_at + 15min（BR-03）。
 */
@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ORD+时间戳+6位随机，UNIQUE（实现细节） */
    private String orderNo;

    private Long goodsId;

    private Long buyerId;

    private Long sellerId;

    /** 下单时价格快照（BR-12，商品后续改价不影响） */
    private Double amount;

    private OrderStatus status;

    /** 模拟支付凭证：PAY+时间戳+4位随机（BR-04，无真实资金流） */
    private String payVoucherNo;

    private Long payTime;

    private Long completeTime;

    private String cancelReason;

    /** 支付截止时间（epoch ms），超时由 T-11 定时任务关闭 */
    private Long timeoutAt;

    private Long createdAt;
}