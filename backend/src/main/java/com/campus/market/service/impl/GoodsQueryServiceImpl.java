package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.common.PageResult;
import com.campus.market.dto.response.GoodsDetailVO;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.dto.response.MyGoodsVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.SortType;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.GoodsQueryService;
import com.campus.market.service.ReviewService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品读服务（FR-GOODS-05~08；API-14/15/16）。
 * 可见性：列表仅 ON_SALE（游客视角，FR-GOODS-05）；详情非在售仅属主可见（FR-GOODS-06）；
 * 分页 size ≤ 100（02§2.5.1）；排序默认 LATEST 走 idx_goods_status_published 索引（NFR-PERF-01）。
 */
@Service
@RequiredArgsConstructor
public class GoodsQueryServiceImpl implements GoodsQueryService {

    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final ReviewService reviewService;

    @Override
    public PageResult<GoodsVO> list(String keyword, Long categoryId, Double minPrice, Double maxPrice,
                                    String sort, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);

        LambdaQueryWrapper<Goods> qw = new LambdaQueryWrapper<>();
        qw.eq(Goods::getStatus, GoodsStatus.ON_SALE);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(Goods::getTitle, kw).or().like(Goods::getDescription, kw));
        }
        if (categoryId != null) {
            qw.eq(Goods::getCategoryId, categoryId);
        }
        if (minPrice != null) {
            qw.ge(Goods::getPrice, minPrice);
        }
        if (maxPrice != null) {
            qw.le(Goods::getPrice, maxPrice);
        }
        SortType sortType = parseSort(sort);
        switch (sortType) {
            case PRICE_ASC -> qw.orderByAsc(Goods::getPrice);
            case PRICE_DESC -> qw.orderByDesc(Goods::getPrice);
            default -> qw.orderByDesc(Goods::getPublishedAt);
        }

        Page<Goods> result = goodsMapper.selectPage(new Page<>(p, s), qw);
        List<GoodsVO> vos = result.getRecords().stream()
                .map(g -> GoodsVO.from(g, parseImages(g), null))
                .toList();
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    @Override
    public GoodsDetailVO detail(Long goodsId, Long viewerId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品已下架或不存在");
        }
        boolean owner = viewerId != null && viewerId.equals(goods.getSellerId());
        if (goods.getStatus() != GoodsStatus.ON_SALE && !owner) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品已下架或不存在");   // FR-GOODS-06
        }
        User seller = userMapper.selectById(goods.getSellerId());
        String sellerNickname = seller == null ? null : seller.getNickname();
        // 卖家信用分（FR-REVIEW-02，T-13 联动）：无评价返回 null（前端"暂无评价"）
        return GoodsDetailVO.of(goods, parseImages(goods), sellerNickname,
                reviewService.creditOf(goods.getSellerId()), null);
    }

    @Override
    public MyGoodsVO myGoods(Long userId) {
        List<Goods> all = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getSellerId, userId)
                .orderByDesc(Goods::getCreatedAt));
        Map<String, List<GoodsVO>> groups = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Goods g : all) {
            groups.computeIfAbsent(g.getStatus().name(), k -> new ArrayList<>())
                    .add(GoodsVO.from(g, parseImages(g), null));
            counts.merge(g.getStatus().name(), 1L, Long::sum);
        }
        return new MyGoodsVO(groups, counts);
    }

    private SortType parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SortType.LATEST;
        }
        try {
            return SortType.valueOf(sort.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SortType.LATEST;     // 非法排序值宽松降级为默认（FR-GOODS-07 空态/容错）
        }
    }

    private List<String> parseImages(Goods goods) {
        try {
            return objectMapper.readValue(goods.getImagesJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}