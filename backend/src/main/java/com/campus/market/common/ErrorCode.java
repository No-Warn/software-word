package com.campus.market.common;

import lombok.Getter;

/**
 * 统一错误码（契约来源：设计文档 02§2.5.3，唯一口径）。
 * 命名规则：A0xxx=通用/鉴权类，B0xxx=业务类，C0500=系统兜底。
 */
@Getter
public enum ErrorCode {

    SUCCESS("00000", 200, "ok"),

    // 通用
    BAD_REQUEST("A0400", 400, "参数校验失败"),
    UNAUTHORIZED("A0301", 401, "未登录或凭证失效"),
    FORBIDDEN("A0302", 403, "无权限"),
    LOGIN_LOCKED("A0230", 401, "登录失败次数过多，请10分钟后再试"),
    ACCOUNT_BANNED("A0240", 401, "账号已封禁"),

    // 用户
    EMAIL_SUFFIX_INVALID("B0101", 400, "仅支持校园邮箱注册"),
    EMAIL_EXISTS("B0102", 400, "邮箱已注册"),

    // 商品
    GOODS_STATUS_INVALID("B0201", 400, "商品状态不允许该操作"),
    IMAGE_INVALID("B0202", 400, "图片数量/大小/格式不合规"),

    // 订单
    GOODS_NOT_ON_SALE("B0301", 400, "商品不可下单（非在售）"),
    GOODS_LOCKED("B0302", 400, "商品已被锁定"),
    SELF_PURCHASE("B0303", 400, "不能购买自己的商品"),
    ORDER_STATUS_INVALID("B0304", 400, "订单状态不允许该操作"),
    ORDER_TIMEOUT("B0305", 400, "订单已超时关闭"),

    // 内容
    SENSITIVE_WORD("B0401", 400, "内容包含敏感词，请修改后重试"),
    REVIEW_INVALID("B0402", 400, "重复评价或无权评价"),
    REPORT_DUPLICATED("B0403", 400, "该对象已举报，请等待处理"),

    // 系统
    INTERNAL_ERROR("C0500", 500, "系统繁忙，请稍后重试");

    private final String code;
    private final int httpStatus;
    private final String message;

    ErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}