package com.campus.market.controller;

import com.campus.market.common.PageResult;
import com.campus.market.common.Result;
import com.campus.market.dto.request.GoodsUpsertRequest;
import com.campus.market.dto.response.GoodsDetailVO;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.GoodsQueryService;
import com.campus.market.service.GoodsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（契约 API-10~15，02§2.5.2）：
 * 写（API-10~13）→ GoodsService；读（API-14/15）→ GoodsQueryService。
 * GET 类浏览接口游客可见（权限矩阵 01§1.4.2）；属主判定用可选身份（FR-GOODS-06）。
 */
@RestController
@RequestMapping("/api/v1/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;
    private final GoodsQueryService goodsQueryService;

    // ---- 读（API-14/15）----

    @GetMapping
    public Result<PageResult<GoodsVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(goodsQueryService.list(keyword, categoryId, minPrice, maxPrice, sort, page, size));
    }

    @GetMapping("/{id}")
    public Result<GoodsDetailVO> detail(@PathVariable Long id) {
        return Result.ok(goodsQueryService.detail(id, SecurityUtils.optionalUserId()));
    }

    // ---- 写（API-10~13）----

    @PostMapping
    public Result<GoodsVO> publish(@Valid @RequestBody GoodsUpsertRequest req) {
        return Result.ok(goodsService.publish(SecurityUtils.currentUserId(), req));
    }

    @PutMapping("/{id}")
    public Result<GoodsVO> edit(@PathVariable Long id, @Valid @RequestBody GoodsUpsertRequest req) {
        return Result.ok(goodsService.edit(SecurityUtils.currentUserId(), id, req));
    }

    @PostMapping("/{id}/shelf")
    public Result<Void> shelf(@PathVariable Long id, @RequestParam boolean onShelf) {
        goodsService.shelf(SecurityUtils.currentUserId(), id, onShelf);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        goodsService.delete(SecurityUtils.currentUserId(), id);
        return Result.ok();
    }
}