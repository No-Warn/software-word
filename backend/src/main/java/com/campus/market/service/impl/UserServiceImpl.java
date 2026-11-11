package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.dto.request.UpdateProfileRequest;
import com.campus.market.dto.response.GoodsVO;
import com.campus.market.dto.response.UserProfileVO;
import com.campus.market.dto.response.UserVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.ReviewService;
import com.campus.market.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户资料服务（FR-AUTH-03、FR-REVIEW-02；API-04/05/07）。
 * 资料修改即时生效（FR-AUTH-03 验收口径）；他人主页含信用分（reviewee 平均）+ 在售商品列表。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ReviewService reviewService;
    private final GoodsMapper goodsMapper;
    private final ObjectMapper objectMapper;

    @Override
    public UserVO me(Long userId) {
        return UserVO.from(require(userId));
    }

    @Override
    public UserVO updateMe(Long userId, UpdateProfileRequest req) {
        User u = require(userId);
        u.setNickname(req.nickname());
        u.setAvatarUrl(req.avatarUrl());
        u.setBio(req.bio() == null ? "" : req.bio());
        userMapper.updateById(u);
        return UserVO.from(u);                                  // 修改即时生效（FR-AUTH-03）
    }

    @Override
    public UserProfileVO profile(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户不存在");
        }
        List<GoodsVO> onSale = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                        .eq(Goods::getSellerId, userId)
                        .eq(Goods::getStatus, GoodsStatus.ON_SALE)
                        .orderByDesc(Goods::getPublishedAt))
                .stream().map(g -> GoodsVO.from(g, parseImages(g), null)).toList();
        return new UserProfileVO(u.getId(), u.getNickname(), u.getAvatarUrl(), u.getBio(),
                reviewService.creditOf(userId), onSale);
    }

    private User require(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return u;
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