package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.common.PageResult;
import com.campus.market.dto.request.MessageCreateRequest;
import com.campus.market.dto.response.MessageVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Message;
import com.campus.market.entity.User;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.MessageMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.MessageService;
import com.campus.market.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 留言服务（FR-MSG-01~03；API-22~24）。
 * BR-11：留言内容命中敏感词拒绝（B0401，SensitiveWordService）；
 * FR-MSG-02：一层回复（parent_id 须属于同一商品）；FR-MSG-03：我的留言查询即标记本人商品未读已读。
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;

    @Override
    public MessageVO create(Long senderId, Long goodsId, MessageCreateRequest req) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || goods.getDeleted() == 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品不存在或已删除");
        }
        String content = req.content().trim();
        if (sensitiveWordService.hits(content)) {
            throw new BizException(ErrorCode.SENSITIVE_WORD);           // B0401（BR-11）
        }
        Long parentId = req.parentId();
        if (parentId != null) {
            Message parent = messageMapper.selectById(parentId);
            if (parent == null || !parent.getGoodsId().equals(goodsId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "回复的留言不存在");   // FR-MSG-02
            }
        }
        Message message = new Message();
        message.setGoodsId(goodsId);
        message.setSenderId(senderId);
        message.setParentId(parentId);
        message.setContent(content);
        message.setIsRead(0);
        message.setDeleted(0);
        message.setCreatedAt(System.currentTimeMillis());
        messageMapper.insert(message);
        return MessageVO.from(message, nicknameOf(senderId));
    }

    @Override
    public List<MessageVO> listByGoods(Long goodsId) {
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getGoodsId, goodsId)
                .orderByAsc(Message::getCreatedAt));                    // FR-MSG-02 时间正序
        Set<Long> senderIds = messages.stream().map(Message::getSenderId).collect(Collectors.toSet());
        Map<Long, String> nicknames = senderIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(senderIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));
        return messages.stream()
                .map(m -> MessageVO.from(m, nicknames.get(m.getSenderId())))
                .toList();
    }

    @Override
    @Transactional
    public PageResult<MessageVO> mine(Long userId, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);  // 02§2.5.1
        // FR-MSG-03：本人发出的 + 本人商品收到的留言
        List<Long> myGoodsIds = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                        .eq(Goods::getSellerId, userId))
                .stream().map(Goods::getId).toList();
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<>();
        if (myGoodsIds.isEmpty()) {
            qw.eq(Message::getSenderId, userId);
        } else {
            qw.and(w -> w.eq(Message::getSenderId, userId).or().in(Message::getGoodsId, myGoodsIds));
        }
        qw.orderByDesc(Message::getCreatedAt);
        Page<Message> result = messageMapper.selectPage(new Page<>(p, s), qw);
        // 查询即标记本人商品留言已读（FR-MSG-03 角标清零口径）
        if (!myGoodsIds.isEmpty()) {
            messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                    .in(Message::getGoodsId, myGoodsIds)
                    .ne(Message::getSenderId, userId)
                    .eq(Message::getIsRead, 0)
                    .set(Message::getIsRead, 1));
        }
        Set<Long> senderIds = result.getRecords().stream()
                .map(Message::getSenderId).collect(Collectors.toSet());
        Map<Long, String> nicknames = senderIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(senderIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));
        List<MessageVO> vos = result.getRecords().stream()
                .map(m -> MessageVO.from(m, nicknames.get(m.getSenderId())))
                .toList();
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    private String nicknameOf(Long userId) {
        User u = userMapper.selectById(userId);
        return u == null ? null : u.getNickname();
    }
}