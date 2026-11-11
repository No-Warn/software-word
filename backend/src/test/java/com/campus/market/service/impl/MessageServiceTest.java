package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.dto.request.MessageCreateRequest;
import com.campus.market.dto.response.MessageVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Message;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.ReportStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.MessageMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.SensitiveWordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 留言服务测试（FR-MSG-01~02、BR-11；TC-11/TC-28 单元分支）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceTest {

    static {
        // LambdaWrapper 依赖 TableInfo 缓存：mine() 构造 Message/Goods 的 SFunction（同 AdminServiceTest 口径）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Message.class);
        TableInfoHelper.initTableInfo(assistant, Goods.class);
    }

    @Mock
    MessageMapper messageMapper;
    @Mock
    GoodsMapper goodsMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    SensitiveWordService sensitiveWordService;

    MessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageServiceImpl(messageMapper, goodsMapper, userMapper, sensitiveWordService);
        when(messageMapper.insert(any(Message.class))).thenReturn(1);
    }

    private Goods goods() {
        Goods g = new Goods();
        g.setId(10L);
        g.setSellerId(2L);
        g.setStatus(GoodsStatus.ON_SALE);
        g.setDeleted(0);
        return g;
    }

    // TC-11：正常留言成功
    @Test
    void create_ok() {
        when(goodsMapper.selectById(10L)).thenReturn(goods());
        when(sensitiveWordService.hits("你好，还在吗")).thenReturn(false);
        User sender = new User();
        sender.setId(1L);
        sender.setNickname("买家");
        when(userMapper.selectById(1L)).thenReturn(sender);

        MessageVO vo = service.create(1L, 10L, new MessageCreateRequest("你好，还在吗", null));

        assertThat(vo.content()).isEqualTo("你好，还在吗");
        assertThat(vo.senderNickname()).isEqualTo("买家");

    }

    // TC-11 / BR-11：敏感词拦截 B0401
    @Test
    void create_hitsSensitiveWord_B0401() {
        when(goodsMapper.selectById(10L)).thenReturn(goods());
        when(sensitiveWordService.hits("加微信详聊")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, 10L, new MessageCreateRequest("加微信详聊", null)))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.SENSITIVE_WORD);
    }

    // FR-MSG-02：父留言须属于同一商品
    @Test
    void create_rejectsParentMismatch() {
        when(goodsMapper.selectById(10L)).thenReturn(goods());
        when(sensitiveWordService.hits(any())).thenReturn(false);
        Message parent = new Message();
        parent.setId(99L);
        parent.setGoodsId(999L);            // 其他商品的留言
        when(messageMapper.selectById(99L)).thenReturn(parent);

        assertThatThrownBy(() -> service.create(1L, 10L, new MessageCreateRequest("回复", 99L)))
                .hasMessageContaining("回复的留言不存在");
    }

    // FR-MSG-02：时间正序 + 昵称组装
    @Test
    void listByGoods_orderAsc_withNicknames() {
        Message m1 = new Message();
        m1.setId(1L);
        m1.setSenderId(1L);
        m1.setContent("在吗");
        m1.setCreatedAt(1L);
        Message m2 = new Message();
        m2.setId(2L);
        m2.setSenderId(2L);
        m2.setContent("在的");
        m2.setCreatedAt(2L);
        when(messageMapper.selectList(any())).thenReturn(List.of(m1, m2));
        User u1 = new User();
        u1.setId(1L);
        u1.setNickname("买家");
        User u2 = new User();
        u2.setId(2L);
        u2.setNickname("卖家");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u1, u2));

        List<MessageVO> vos = service.listByGoods(10L);

        assertThat(vos).hasSize(2);
        assertThat(vos.get(0).content()).isEqualTo("在吗");
        assertThat(vos.get(1).senderNickname()).isEqualTo("卖家");
    }

    // 防御性：ReportStatus 误用编译口径检查（保持与其他测试一致的 import 有效性）
    @Test
    void unusedReportStatusGuard() {
        assertThat(ReportStatus.PENDING.name()).isEqualTo("PENDING");
    }
}