package com.campus.market.service;

import com.campus.market.dto.request.UpdateProfileRequest;
import com.campus.market.dto.response.UserProfileVO;
import com.campus.market.dto.response.UserVO;

/**
 * 用户资料服务（FR-AUTH-03、FR-REVIEW-02；契约 API-04/05/07）。
 */
public interface UserService {

    /** 我的资料（API-04） */
    UserVO me(Long userId);

    /** 修改资料（API-05，FR-AUTH-03）：昵称 2-20 字符，修改即时生效 */
    UserVO updateMe(Long userId, UpdateProfileRequest req);

    /** 他人主页（API-07）：用户信息 + 信用分 + 在售商品 */
    UserProfileVO profile(Long userId);
}