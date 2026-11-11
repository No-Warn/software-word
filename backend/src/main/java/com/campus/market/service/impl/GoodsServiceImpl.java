package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.dto.request.GoodsUpsertRequest;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.entity.Category;
import com.campus.market.entity.Goods;
import com.campus.market.enums.GoodsEvent;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.service.GoodsService;
import com.campus.market.statemachine.GoodsStateMachine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品写服务（FR-GOODS-01~04；API-10~13）。
 * 状态流转三重防线：状态机守卫（BR-02）→ 带状态条件 UPDATE（乐观锁语义）→ 影响行数校验（竞态兜底）。
 * 属主校验（NFR-SEC-02）：仅本人商品可写，越权返回 A0302。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsServiceImpl implements GoodsService {

    private final GoodsMapper goodsMapper;
    private final CategoryMapper categoryMapper;
    private final GoodsStateMachine goodsStateMachine;
    private final ObjectMapper objectMapper;

    @Override
    public GoodsVO publish(Long sellerId, GoodsUpsertRequest req) {
        validate(req);                                          // BR-09 / BR-10 防御校验
        Category category = requireCategory(req.categoryId());
        Goods goods = new Goods();
        applyUpsert(goods, req);
        goods.setSellerId(sellerId);
        goods.setStatus(GoodsStatus.PENDING_AUDIT);             // FR-GOODS-01：发布即待审核
        goods.setDeleted(0);
        long now = System.currentTimeMillis();
        goods.setCreatedAt(now);
        goods.setUpdatedAt(now);
        goodsMapper.insert(goods);
        return GoodsVO.from(goods, parseImages(goods), category.getName());
    }

    @Override
    public GoodsVO edit(Long userId, Long goodsId, GoodsUpsertRequest req) {
        validate(req);
        Goods goods = requireOwnedGoods(userId, goodsId);
        if (goods.getStatus() == GoodsStatus.LOCKED || goods.getStatus() == GoodsStatus.SOLD) {
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID, "交易中/已售出商品不可编辑");
        }
        boolean keyFieldsChanged = keyFieldsChanged(goods, req);
        applyUpsert(goods, req);
        goods.setUpdatedAt(System.currentTimeMillis());
        goodsMapper.updateById(goods);

        // FR-GOODS-02：编辑后的状态语义
        switch (goods.getStatus()) {
            case ON_SALE, OFF_SHELF -> {
                if (keyFieldsChanged) {
                    transition(goods, GoodsEvent.EDIT_REVISE);  // 关键字段变更 → 回退待审核
                }
            }
            case REJECTED -> transition(goods, GoodsEvent.SUBMIT);  // 修改后重新提交（清驳回原因）
            case DRAFT, PENDING_AUDIT -> { /* 状态保持 */ }
            default -> throw new BizException(ErrorCode.GOODS_STATUS_INVALID);
        }
        if (goods.getStatus() == GoodsStatus.PENDING_AUDIT) {
            goods.setRejectReason(null);
            goodsMapper.updateById(goods);
        }
        return GoodsVO.from(goods, parseImages(goods), null);
    }

    @Override
    public void shelf(Long userId, Long goodsId, boolean onShelf) {
        Goods goods = requireOwnedGoods(userId, goodsId);
        if (onShelf) {
            transition(goods, GoodsEvent.SUBMIT);       // OFF_SHELF → PENDING_AUDIT（BR-05 需再审核）
        } else {
            transition(goods, GoodsEvent.OFF_SHELF);    // ON_SALE → OFF_SHELF
        }
    }

    @Override
    public void delete(Long userId, Long goodsId) {
        Goods goods = requireOwnedGoods(userId, goodsId);
        // FR-GOODS-04：仅 DRAFT/OFF_SHELF/REJECTED 可删（ON_SALE 需先下架，LOCKED/SOLD 禁止）
        if (goods.getStatus() != GoodsStatus.DRAFT
                && goods.getStatus() != GoodsStatus.OFF_SHELF
                && goods.getStatus() != GoodsStatus.REJECTED) {
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID, "当前状态不可删除（在售商品请先下架）");
        }
        goodsMapper.deleteById(goods.getId());          // @TableLogic 逻辑删除
    }

    /**
     * 状态流转（三重防线）。非法迁移由状态机抛 B0201；条件更新影响行数≠1 视为竞态冲突。
     */
    private void transition(Goods goods, GoodsEvent event) {
        GoodsStatus from = goods.getStatus();
        goodsStateMachine.check(from, event);           // BR-02 守卫
        GoodsStatus to = goodsStateMachine.target(from, event);
        int rows = goodsMapper.transitionStatus(goods.getId(), from.name(), to.name(),
                System.currentTimeMillis());
        if (rows != 1) {
            log.warn("goods status transition race: id={} {} -> {}", goods.getId(), from, to);
            throw new BizException(ErrorCode.GOODS_STATUS_INVALID);
        }
        goods.setStatus(to);
        if (to == GoodsStatus.PENDING_AUDIT) {
            goods.setRejectReason(null);
        }
    }

    private void validate(GoodsUpsertRequest req) {
        if (req.price() == null || req.price() < 0.01 || req.price() > 99999.99) {
            throw new BizException(ErrorCode.BAD_REQUEST, "价格须在 0.01~99999.99 元");   // BR-09
        }
        if (req.images() == null || req.images().isEmpty() || req.images().size() > 9) {
            throw new BizException(ErrorCode.IMAGE_INVALID);      // BR-10
        }
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分类不存在");
        }
        return category;
    }

    private Goods requireOwnedGoods(Long userId, Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品不存在或已删除");
        }
        if (!goods.getSellerId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);          // NFR-SEC-02 越权
        }
        return goods;
    }

    private void applyUpsert(Goods goods, GoodsUpsertRequest req) {
        goods.setTitle(req.title());
        goods.setDescription(nullSafe(req.description()));
        goods.setCategoryId(req.categoryId());
        goods.setCondition(req.condition());
        goods.setPrice(req.price());
        goods.setImagesJson(toJson(req.images()));
    }

    private boolean keyFieldsChanged(Goods goods, GoodsUpsertRequest req) {
        // 关键字段：标题/描述/价格/图片（FR-GOODS-02）
        return !goods.getTitle().equals(req.title())
                || !nullSafe(goods.getDescription()).equals(nullSafe(req.description()))
                || Double.compare(goods.getPrice(), req.price()) != 0
                || !goods.getImagesJson().equals(toJson(req.images()));
    }

    private String toJson(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
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

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}