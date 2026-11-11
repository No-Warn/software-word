package com.campus.market.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证全链路集成测试（临时 SQLite 文件库）：
 * 注册→登录→改密→旧 token 失效（FR-AUTH-01/02/04；TC-01/TC-02/TC-17 链路证据）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowMockMvcTest {

    @Autowired
    MockMvc mockMvc;

    static Path dbFile;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("market-auth-", ".db");
        Files.deleteIfExists(dbFile);       // SQLite 需要新建文件
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbFile);
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    private String postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @Order(1)
    void fullAuthFlow() throws Exception {
        // 1) 注册成功（TC-01 合法分支）并自动登录
        String reg = postJson("/api/v1/auth/register",
                "{\"email\":\"stu1@stu.example.edu.cn\",\"nickname\":\"张三\",\"password\":\"abcd1234\"}");
        assertThat(JsonPath.<String>read(reg, "$.code")).isEqualTo("00000");
        String token1 = JsonPath.read(reg, "$.data.token");

        // 2) 非校园邮箱 → B0101（BR-01）
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"out@gmail.com\",\"nickname\":\"李四\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0101"));

        // 3) 重复邮箱 → B0102
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stu1@stu.example.edu.cn\",\"nickname\":\"王五\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0102"));

        // 4) 错误密码 → 401 且统一文案（防枚举）
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stu1@stu.example.edu.cn\",\"password\":\"badpass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("邮箱或密码错误"));

        // 5) 正确登录 → token2
        String login = postJson("/api/v1/auth/login",
                "{\"email\":\"stu1@stu.example.edu.cn\",\"password\":\"abcd1234\"}");
        assertThat(JsonPath.<String>read(login, "$.code")).isEqualTo("00000");
        String token2 = JsonPath.read(login, "$.data.token");
        assertThat(token2).isNotBlank();

        // 6) 旧 token 有效（改密前）：PUT /users/me/password 旧密码错误 → 400（而非 401）
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong-old\",\"newPassword\":\"newpass99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("旧密码错误"));

        // 7) 新 token 改密成功 → 200（FR-AUTH-04）
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"abcd1234\",\"newPassword\":\"newpass99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));

        // 8) 改密后旧 token 全部失效 → 401（TC-17 / CH-007 机制）
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isUnauthorized());

        // 9) 新密码可登录
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stu1@stu.example.edu.cn\",\"password\":\"newpass99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }
}