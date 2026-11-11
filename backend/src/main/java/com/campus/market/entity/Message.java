package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 留言实体（表结构 02§4.2 message）。一层回复（parent_id）；BR-11 敏感词过滤在 Service 层。
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long goodsId;

    private Long senderId;

    /** 父留言 id（一层回复，FR-MSG-02） */
    private Long parentId;

    private String content;

    /** 未读标记（FR-MSG-03：收件人查看后清零） */
    private Integer isRead;

    @TableLogic
    private Integer deleted;

    private Long createdAt;
}