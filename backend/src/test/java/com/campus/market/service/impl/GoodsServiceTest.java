package com.campus.market.service.impl;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.dto.request.GoodsUpsertRequest;
import com.campus.market.entity.Category;
import com.campus.market.entity.Goods;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.statemachine.GoodsStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品写服务测试（FR-GOODS-01~04、BR-02/09/10；TC-08 核心分支）。
 * 状态机为真实实例（迁移表覆盖由 GoodsStateMachineTest 保证），Mapper 为 Mock。
 * 注意：Mockito 同一调用内不得混用字面量与 matcher，统一使用 eq()。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsServiceTest {

    @Mock
    GoodsMapper goodsMapper;
    @Mock
    CategoryMapper categoryMapper;

    GoodsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoodsServiceImpl(goodsMapper, categoryMapper,
                new GoodsStateMachine(), new ObjectMapper());
        Category category = new Category();
        category.setId(1L);
        category.setName("教材");
        when(categoryMapper.selectById(1L)).thenReturn(category);
        when(goodsMapper.insert(any(Goods.class))).thenReturn(1);
        when(goodsMapper.transitionStatus(anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);
    }

    private GoodsUpsertRequest req() {
        return new GoodsUpsertRequest("高数教材", "九成新", 1L, "LIKE_NEW", 9.9,
                List.of("/api/v1/files/a.jpg"));
    }

    private Goods goods(GoodsStatus status) {
        Goods g = new Goods();
        g.setId(100L);
        g.setSellerId(1L);
        g.setCategoryId(1L);
        g.setTitle("高数教材");
        g.setDescription("九成新");
        g.setCondition("LIKE_NEW");
        g.setPrice(9.9);
        g.setImagesJson("[\"/api/v1/files/a.jpg\"]");
        g.setStatus(status);
        g.setDeleted(0);
        return g;
    }

    // ---- FR-GOODS-01（TC-18 后端分支）----

    @Test
    void publish_ok_createdAsPendingAudit() {
        var vo = service.publish(1L, req());
        ArgumentCaptor<Goods> captor = ArgumentCaptor.forClass(Goods.class);
        verify(goodsMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GoodsStatus.PENDING_AUDIT);
        assertThat(vo.status()).isEqualTo(GoodsStatus.PENDING_AUDIT);
        assertThat(vo.categoryName()).isEqualTo("教材");
    }

    @Test
    void publish_rejectsPriceOutOfRange_BR09() {
        var bad = new GoodsUpsertRequest("x", "", 1L, "NEW", 0.001, List.of("/a.jpg"));
        assertThatThrownBy(() -> service.publish(1L, bad))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("价格须在");
        var tooBig = new GoodsUpsertRequest("x", "", 1L, "NEW", 100000.0, List.of("/a.jpg"));
        assertThatThrownBy(() -> service.publish(1L, tooBig))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("价格须在");
    }

    @Test
    void publish_rejectsTooManyImages_BR10() {
        var many = new GoodsUpsertRequest("x", "", 1L, "NEW", 9.9,
                List.of("/1.jpg", "/2.jpg", "/3.jpg", "/4.jpg", "/5.jpg", "/6.jpg", "/7.jpg", "/8.jpg", "/9.jpg", "/10.jpg"));
        assertThatThrownBy(() -> service.publish(1L, many))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_INVALID);
    }

    @Test
    void publish_rejectsUnknownCategory() {
        when(categoryMapper.selectById(99L)).thenReturn(null);
        var bad = new GoodsUpsertRequest("x", "", 99L, "NEW", 9.9, List.of("/a.jpg"));
        assertThatThrownBy(() -> service.publish(1L, bad))
                .hasMessageContaining("分类不存在");
    }

    // ---- FR-GOODS-02（TC-08）----

    @Test
    void edit_byNonOwner_forbidden() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.ON_SALE));
        assertThatThrownBy(() -> service.edit(2L, 100L, req()))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void edit_lockedGoods_rejected() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.LOCKED));
        assertThatThrownBy(() -> service.edit(1L, 100L, req()))
                .hasMessageContaining("不可编辑");
    }

    @Test
    void edit_onSaleKeyChange_revertsToPendingAudit_TC08() {
        Goods onSale = goods(GoodsStatus.ON_SALE);
        when(goodsMapper.selectById(100L)).thenReturn(onSale);
        var changed = new GoodsUpsertRequest("高数教材(第八版)", "九成新", 1L, "LIKE_NEW", 19.9,
                List.of("/api/v1/files/a.jpg"));

        var vo = service.edit(1L, 100L, changed);

        verify(goodsMapper).transitionStatus(eq(100L), eq("ON_SALE"), eq("PENDING_AUDIT"), anyLong());
        assertThat(vo.status()).isEqualTo(GoodsStatus.PENDING_AUDIT);
        assertThat(onSale.getRejectReason()).isNull();
    }

    @Test
    void edit_onSaleNoKeyChange_keepsOnSale() {
        Goods onSale = goods(GoodsStatus.ON_SALE);
        when(goodsMapper.selectById(100L)).thenReturn(onSale);

        var vo = service.edit(1L, 100L, req());   // 与现值完全一致

        verify(goodsMapper, never()).transitionStatus(anyLong(), anyString(), anyString(), anyLong());
        assertThat(vo.status()).isEqualTo(GoodsStatus.ON_SALE);
    }

    @Test
    void edit_rejectedGoods_resubmitsForAudit() {
        Goods rejected = goods(GoodsStatus.REJECTED);
        rejected.setRejectReason("图片模糊");
        when(goodsMapper.selectById(100L)).thenReturn(rejected);

        service.edit(1L, 100L, req());

        verify(goodsMapper).transitionStatus(eq(100L), eq("REJECTED"), eq("PENDING_AUDIT"), anyLong());
        assertThat(rejected.getRejectReason()).isNull();
    }

    // ---- FR-GOODS-03（TC-19）----

    @Test
    void shelf_off_fromOnSale_ok() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.ON_SALE));
        service.shelf(1L, 100L, false);
        verify(goodsMapper).transitionStatus(eq(100L), eq("ON_SALE"), eq("OFF_SHELF"), anyLong());
    }

    @Test
    void shelf_on_fromOffShelf_needsReaudit_BR05() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.OFF_SHELF));
        service.shelf(1L, 100L, true);
        verify(goodsMapper).transitionStatus(eq(100L), eq("OFF_SHELF"), eq("PENDING_AUDIT"), anyLong());
    }

    @Test
    void shelf_on_fromOnSale_rejected_B0201() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.ON_SALE));
        assertThatThrownBy(() -> service.shelf(1L, 100L, true))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOODS_STATUS_INVALID);
    }

    // ---- FR-GOODS-04（TC-20）----

    @Test
    void delete_rejectsOnSale() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.ON_SALE));
        assertThatThrownBy(() -> service.delete(1L, 100L))
                .hasMessageContaining("不可删除");
        verify(goodsMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_ok_offShelf() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.OFF_SHELF));
        service.delete(1L, 100L);
        verify(goodsMapper).deleteById(eq(100L));       // @TableLogic 逻辑删除
    }

    // ---- 竞态兜底（02§2.6.5 条件更新行数校验）----

    @Test
    void transition_race_fallsBackToB0201() {
        Goods onSale = goods(GoodsStatus.ON_SALE);
        when(goodsMapper.selectById(100L)).thenReturn(onSale);
        when(goodsMapper.transitionStatus(anyLong(), anyString(), anyString(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.shelf(1L, 100L, false))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOODS_STATUS_INVALID);
    }
}