package com.campus.market.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BizException;
import com.campus.market.dto.response.GoodsDetailVO;
import com.campus.market.dto.response.MyGoodsVO;
import com.campus.market.entity.Goods;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 商品读服务测试（FR-GOODS-05/06/07/08；TC-21/22/24 单元分支）。
 * 查询条件组合与 LIKE 命中由集成测试 TC-21/TC-23 覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsQueryServiceTest {

    @Mock
    GoodsMapper goodsMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    ReviewService reviewService;

    GoodsQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoodsQueryServiceImpl(goodsMapper, userMapper, new ObjectMapper(), reviewService);
    }

    private Goods goods(GoodsStatus status) {
        Goods g = new Goods();
        g.setId(100L);
        g.setSellerId(1L);
        g.setCategoryId(1L);
        g.setTitle("考研数学教材");
        g.setDescription("含真题解析");
        g.setCondition("LIKE_NEW");
        g.setPrice(9.9);
        g.setImagesJson("[\"/api/v1/files/a.jpg\"]");
        g.setStatus(status);
        g.setDeleted(0);
        g.setCreatedAt(1L);
        g.setPublishedAt(2L);
        return g;
    }

    // ---- FR-GOODS-06（TC-22）详情可见性三分支 ----

    @Test
    void detail_guestBlockedWhenNotOnSale() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.OFF_SHELF));
        assertThatThrownBy(() -> service.detail(100L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品已下架或不存在");
    }

    @Test
    void detail_ownerCanViewOwnOffShelf() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.OFF_SHELF));
        var vo = service.detail(100L, 1L);
        assertThat(vo.status()).isEqualTo(GoodsStatus.OFF_SHELF);
    }

    @Test
    void detail_guestCanViewOnSale() {
        when(goodsMapper.selectById(100L)).thenReturn(goods(GoodsStatus.ON_SALE));
        var vo = service.detail(100L, null);
        assertThat(vo.title()).isEqualTo("考研数学教材");
    }

    @Test
    void detail_missingRejected() {
        when(goodsMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.detail(999L, null))
                .hasMessageContaining("商品已下架或不存在");
    }

    // ---- FR-GOODS-08（TC-24）分组统计 ----

    @Test
    void myGoods_groupedWithCounts() {
        when(goodsMapper.selectList(any())).thenReturn(List.of(
                goods(GoodsStatus.ON_SALE),
                goods(GoodsStatus.ON_SALE),
                goods(GoodsStatus.OFF_SHELF)));
        MyGoodsVO vo = service.myGoods(1L);
        assertThat(vo.groups().keySet()).containsExactlyInAnyOrder("ON_SALE", "OFF_SHELF");
        assertThat(vo.groups().get("ON_SALE")).hasSize(2);
        assertThat(vo.counts()).containsEntry("ON_SALE", 2L).containsEntry("OFF_SHELF", 1L);
    }

    // ---- FR-GOODS-05/07（TC-21/23 单元分支）分页参数 ----

    @Test
    @SuppressWarnings("unchecked")
    void list_capsPageSizeAt100() {
        when(goodsMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<Goods> pg = inv.getArgument(0);
            pg.setRecords(List.of(goods(GoodsStatus.ON_SALE)));
            pg.setTotal(1);
            return pg;
        });

        var result = service.list(null, null, null, null, null, 2, 500);

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(100);    // 上限 100（02§2.5.1）
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_defaultPaginationAndUnknownSortFallsBack() {
        when(goodsMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<Goods> pg = inv.getArgument(0);
            pg.setRecords(List.of());
            pg.setTotal(0);
            return pg;
        });

        var result = service.list("  ", null, null, null, "WEIRD", null, null);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getList()).isEmpty();         // 空态（FR-GOODS-07）
    }
}