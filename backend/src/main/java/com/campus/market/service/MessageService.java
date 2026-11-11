package com.campus.market.service;

import com.campus.market.common.PageResult;
import com.campus.market.dto.request.MessageCreateRequest;
import com.campus.market.dto.response.MessageVO;

import java.util.List;

/**
 * 留言服务（FR-MSG-01~03；契约 API-22~24）。
 */
public interface MessageService {

    /** 发表留言/回复（FR-MSG-01）：BR-11 敏感词过滤（B0401）；父留言存在性校验 */
    MessageVO create(Long senderId, Long goodsId, MessageCreateRequest req);

    /** 商品留言列表（API-23）：游客可见，按时间正序，含一层回复 */
    List<MessageVO> listByGoods(Long goodsId);

    /** 我的留言（API-24，FR-MSG-03）：本人发出的 + 本人商品收到的留言；查询即标记已读 */
    PageResult<MessageVO> mine(Long userId, Integer page, Integer size);
}