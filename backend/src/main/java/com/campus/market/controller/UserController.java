package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.request.ChangePasswordRequest;
import com.campus.market.dto.response.MyGoodsVO;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.AuthService;
import com.campus.market.service.GoodsQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（契约 02§2.5.2）：改密 API-06（T-03）、我的商品 API-16（T-07）；
 * /users/me 资料读写（API-04/05）与他人主页 API-07 归 T-04 增量补充。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final GoodsQueryService goodsQueryService;

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(SecurityUtils.currentUserId(), req.oldPassword(), req.newPassword());
        return Result.ok();
    }

    @GetMapping("/me/goods")
    public Result<MyGoodsVO> myGoods() {
        return Result.ok(goodsQueryService.myGoods(SecurityUtils.currentUserId()));
    }
}