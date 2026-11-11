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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理流集成测试（FR-ADMIN-01/03；TC-07/TC-10 端到端 + NFR-SEC-02 越权 403）。
 * ADMIN_PASSWORD 经 Spring 属性注入（AdminInitializer 可测试性，见 CH-008 后续登记）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminFlowIT {

    @Autowired
    MockMvc mockMvc;

    static Path dbFile;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("market-admin-", ".db");
        Files.deleteIfExists(dbFile);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbFile);
        registry.add("ADMIN_PASSWORD", () -> "test-admin-pass-123");
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    private String postJson(String url, String json, String token) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        // 显式 UTF-8：MockMvc 对无 charset 响应默认 ISO-8859-1，中文 contains 断言需正确解码
        return mockMvc.perform(req).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String getJson(String url, String token) throws Exception {
        var req = get(url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(req).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String login(String email, String password) throws Exception {
        String body = postJson("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        return JsonPath.read(body, "$.data.token");
    }

    private Long registerSeller(String email) throws Exception {
        String body = postJson("/api/v1/auth/register",
                "{\"email\":\"" + email + "\",\"nickname\":\"卖家\",\"password\":\"abcd1234\"}", null);
        // Object 中间变量：避免编译器把 <T> read() 推断为 char[]（String.valueOf 重载陷阱）
        Object id = JsonPath.read(body, "$.data.userInfo.id");
        return Long.valueOf(String.valueOf(id));
    }

    private Long publishGoods(String token, String title) throws Exception {
        String body = postJson("/api/v1/goods",
                "{\"title\":\"" + title + "\",\"description\":\"..\",\"categoryId\":1,"
                        + "\"condition\":\"NEW\",\"price\":9.9,\"images\":[\"/api/v1/files/a.jpg\"]}",
                token);
        Object id = JsonPath.read(body, "$.data.id");
        return Long.valueOf(String.valueOf(id));
    }

    // TC-07 端到端：发布→待审核队列→审核通过→游客列表可见
    @Test
    @Order(1)
    void tc07_auditFlow_endToEnd() throws Exception {
        String adminToken = login("admin@campus.local", "test-admin-pass-123");
        registerSeller("tc07-seller@stu.example.edu.cn");
        String sellerToken = login("tc07-seller@stu.example.edu.cn", "abcd1234");
        Long goodsId = publishGoods(sellerToken, "TC07 审核商品");

        // 游客列表不可见（PENDING_AUDIT，FR-GOODS-05）
        assertThat(getJson("/api/v1/goods?size=50", null)).doesNotContain("TC07 审核商品");

        // 管理员待审核队列可见（API-29）
        assertThat(getJson("/api/v1/admin/goods/pending", adminToken)).contains("TC07 审核商品");

        // 审核通过（API-30）
        mockMvc.perform(post("/api/v1/admin/goods/" + goodsId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        // TC-07 验收核心：通过后首页可见（游客）
        mockMvc.perform(get("/api/v1/goods?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.title=='TC07 审核商品')]").isNotEmpty());

        // 游客详情可见（FR-GOODS-06）
        mockMvc.perform(get("/api/v1/goods/" + goodsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    // 驳回原因必填分支（FR-ADMIN-01）
    @Test
    @Order(2)
    void auditRejectRequiresReason() throws Exception {
        String adminToken = login("admin@campus.local", "test-admin-pass-123");
        registerSeller("tc07b-seller@stu.example.edu.cn");
        String sellerToken = login("tc07b-seller@stu.example.edu.cn", "abcd1234");
        Long goodsId = publishGoods(sellerToken, "TC07B 驳回商品");

        mockMvc.perform(post("/api/v1/admin/goods/" + goodsId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("驳回原因必填"));
    }

    // 越权：普通用户调管理接口 → 403（NFR-SEC-02 双保险验证）
    @Test
    @Order(3)
    void adminEndpointsBlockedForNonAdmin() throws Exception {
        registerSeller("tc07c-seller@stu.example.edu.cn");
        String sellerToken = login("tc07c-seller@stu.example.edu.cn", "abcd1234");
        mockMvc.perform(get("/api/v1/admin/goods/pending")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden());
    }

    // TC-10 端到端：封禁联动下架（BR-08）+ 被封禁用户登录被拒（A0240）
    @Test
    @Order(4)
    void tc10_banFlow_endToEnd() throws Exception {
        String adminToken = login("admin@campus.local", "test-admin-pass-123");
        Long sellerId = registerSeller("tc10-seller@stu.example.edu.cn");
        String sellerToken = login("tc10-seller@stu.example.edu.cn", "abcd1234");
        Long goodsId = publishGoods(sellerToken, "TC10 封禁商品");

        mockMvc.perform(post("/api/v1/admin/goods/" + goodsId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/" + sellerId + "/ban?banned=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // BR-08 端到端：其商品从游客列表消失
        mockMvc.perform(get("/api/v1/goods?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.title=='TC10 封禁商品')]").isEmpty());

        // 被封禁用户登录被拒（A0240）
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tc10-seller@stu.example.edu.cn\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0240"));
    }
}