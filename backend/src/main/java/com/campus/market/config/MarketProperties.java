package com.campus.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * market.* 配置属性（来源：03§3.4 配置项表 + application.yml）。
 * 设计文档 02§2.3.1 中的 JwtProperties 并入本属性类统一管理（实现细节，口径不变）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "market")
public class MarketProperties {

    private Jwt jwt = new Jwt();
    /** 待支付订单保留分钟数（BR-03，默认 15） */
    private int payTimeoutMinutes = 15;
    private Upload upload = new Upload();
    private String dataDir = "./data";
    /** 校园邮箱后缀白名单（BR-01，Q1 待确认后仅改配置） */
    private String campusEmailSuffix = "@stu.example.edu.cn";
    /** 登录/注册/留言限流：次/分钟/IP（02§2.5.1） */
    private int rateLimitPerMinute = 60;

    @Data
    public static class Jwt {
        private String secret;
        private int expireHours = 24;
    }

    @Data
    public static class Upload {
        private int maxCount = 9;
    }
}