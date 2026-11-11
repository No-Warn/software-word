package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.request.GoodsUpsertRequest;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.GoodsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品写接口（契约 API-10~13，02§2.5.2）。
 * 读接口（API-14~16 列表/详情/我的商品）属 T-07 增量交付。
 */
@RestController
@RequestMapping("/api/v1/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

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