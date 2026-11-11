package com.campus.market.dto.response;

/**
 * 登录/注册响应（契约 API-01/02：{token, userInfo}）。
 */
public record LoginVO(String token, UserVO userInfo) {
}