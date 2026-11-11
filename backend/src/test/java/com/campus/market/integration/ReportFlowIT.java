package com.campus.market.integration;

import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC-31/TC-32 端到端：举报受理→我的举报可见→重复举报 B0403。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportFlowIT {

    @Autowired
    MockMvc mockMvc;

    static Path dbFile;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("market-report-", ".db");
        Files.deleteIfExists(dbFile);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbFile);
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

    private Long registerUser(String email) throws Exception {
        String body = postJson("/api/v1/auth/register",
                "{\"email\":\"" + email + "\",\"nickname\":\"用户\",\"password\":\"abcd1234\"}", null);
        // Object 中间变量：避免 String.valueOf(read()) 被推断为 char[]（JsonPath 泛型陷阱）
        Object id = JsonPath.read(body, "$.data.userInfo.id");
        return Long.valueOf(String.valueOf(id));
    }

    private String login(String email, String password) throws Exception {
        String body = postJson("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        return JsonPath.read(body, "$.data.token");
    }

    // TC-31/TC-32 端到端：受理→可见→重复 B0403
    @Test
    void tc31_reportFlow_endToEnd() throws Exception {
        Long targetId = registerUser("tc31-target@stu.example.edu.cn");
        registerUser("tc31-reporter@stu.example.edu.cn");
        String reporterToken = login("tc31-reporter@stu.example.edu.cn", "abcd1234");

        // 受理（FR-RPRT-01）
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"USER\",\"targetId\":" + targetId
                                + ",\"reason\":\"违规发言\",\"detail\":\"详情\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // FR-RPRT-02：我的举报可见（状态与结论字段齐备）
        mockMvc.perform(get("/api/v1/users/me/reports")
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.list[0].reason").value("违规发言"));

        // TC-31 DoD 核心：重复举报 → B0403"已受理"
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"USER\",\"targetId\":" + targetId
                                + ",\"reason\":\"再次举报\",\"detail\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0403"));

        // 目标不存在 → 400
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"GOODS\",\"targetId\":99999,\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("被举报商品不存在"));
    }
}