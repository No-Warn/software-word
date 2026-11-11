package com.campus.market.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-SEC 补充 / 02§2.5.1 限流：登录接口单 IP 60 次/分钟，第 61 次返回 429。
 * 用不存在的邮箱（统一文案）避免触发账号锁定干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIT {

    @Autowired
    MockMvc mockMvc;

    static Path dbFile;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("market-ratelimit-", ".db");
        Files.deleteIfExists(dbFile);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbFile);
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    @Test
    void loginRateLimitedAt61stCall() throws Exception {
        String body = "{\"email\":\"nobody@stu.example.edu.cn\",\"password\":\"wrong-pass\"}";
        // 前 60 次：正常进入鉴权逻辑（401 统一文案）
        for (int i = 1; i <= 60; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
        // 第 61 次：限流 429（A0429）
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(429));
        // 第 62 次：仍限流（窗口未过）
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(429));
    }
}