package com.campus.market.dto.response;

import com.campus.market.entity.Message;

/**
 * 留言视图（契约 API-22/23/24）：含发送者昵称（Service 关联填充）。
 */
public record MessageVO(
        Long id,
        Long goodsId,
        Long senderId,
        String senderNickname,
        Long parentId,
        String content,
        Long createdAt
) {
    public static MessageVO from(Message m, String senderNickname) {
        return new MessageVO(m.getId(), m.getGoodsId(), m.getSenderId(), senderNickname,
                m.getParentId(), m.getContent(), m.getCreatedAt());
    }
}