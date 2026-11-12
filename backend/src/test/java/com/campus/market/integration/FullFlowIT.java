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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-23 端到端联调冒烟（TC-01~TC-45 全链路浓缩）：
 * 注册→发布→审核→下单→支付→完成→互评→信用分→留言(BR-11)→举报→处理→资料修改→看板→越权。
 * 断言纪律：JsonPath.read 结果赋显式类型变量后再 assertThat（规避 Object 参数在
 * assertThat(Object)/assertThat(Predicate<T>) 间的重载二义）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowIT {

    @Autowired
    MockMvc mockMvc;

    static Path dbFile;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("market-full-", ".db");
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
        return mockMvc.perform(req).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String getJson(String url, String token) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(req).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String login(String email, String password) throws Exception {
        return JsonPath.read(postJson("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null), "$.data.token");
    }

    @Test
    @Order(1)
    void fullChain_endToEnd() throws Exception {
        // 1. 管理员（Initializer 建号）
        String adminToken = login("admin@campus.local", "test-admin-pass-123");

        // 2. 卖家注册 + 发布商品（FR-GOODS-01：PENDING_AUDIT）
        String regSeller = postJson("/api/v1/auth/register",
                "{\"email\":\"full-seller@stu.example.edu.cn\",\"nickname\":\"卖家小王\",\"password\":\"abcd1234\"}", null);
        String sellerCode = JsonPath.read(regSeller, "$.code");
        assertThat(sellerCode).isEqualTo("00000");
        String sellerToken = login("full-seller@stu.example.edu.cn", "abcd1234");
        Long sellerId = ((Number) JsonPath.read(regSeller, "$.data.userInfo.id")).longValue();
        String published = postJson("/api/v1/goods",
                "{\"title\":\"全链路冒烟商品\",\"description\":\"九成新考研教材\",\"categoryId\":1,"
                        + "\"condition\":\"LIKE_NEW\",\"price\":19.9,\"images\":[\"/api/v1/files/a.jpg\"]}",
                sellerToken);
        Long goodsId = ((Number) JsonPath.read(published, "$.data.id")).longValue();

        // 3. 游客列表不可见（FR-GOODS-05）
        assertThat(getJson("/api/v1/goods?size=50", null)).doesNotContain("全链路冒烟商品");

        // 4. 管理员审核通过（TC-07）→ 首页可见
        postJson("/api/v1/admin/goods/" + goodsId + "/audit", "{\"action\":\"APPROVE\"}", adminToken);
        assertThat(goodsStatus(goodsId, sellerToken)).isEqualTo("ON_SALE");
        assertThat(getJson("/api/v1/goods?size=50", null)).contains("全链路冒烟商品");

        // 5. 买家注册 + 下单（FR-ORDER-01）→ 商品 LOCKED
        String regBuyer = postJson("/api/v1/auth/register",
                "{\"email\":\"full-buyer@stu.example.edu.cn\",\"nickname\":\"买家小李\",\"password\":\"abcd1234\"}", null);
        String buyerToken = login("full-buyer@stu.example.edu.cn", "abcd1234");
        Long buyerId = ((Number) JsonPath.read(regBuyer, "$.data.userInfo.id")).longValue();
        String order = postJson("/api/v1/orders", "{\"goodsId\":" + goodsId + "}", buyerToken);
        String orderStatus = JsonPath.read(order, "$.data.status");
        assertThat(orderStatus).isEqualTo("PENDING_PAY");
        Long orderId = ((Number) JsonPath.read(order, "$.data.id")).longValue();
        assertThat(goodsStatus(goodsId, sellerToken)).isEqualTo("LOCKED");

        // 6. 模拟支付（TC-04：凭证 BR-04）
        String paid = postJson("/api/v1/orders/" + orderId + "/pay", "{}", buyerToken);
        String voucher = JsonPath.read(paid, "$.data.payVoucherNo");
        assertThat(voucher).matches("PAY\\d{18}");

        // 7. 确认完成（TC-26）→ 订单 COMPLETED + 商品 SOLD
        String completed = postJson("/api/v1/orders/" + orderId + "/complete", "{}", buyerToken);
        String completedStatus = JsonPath.read(completed, "$.data.status");
        assertThat(completedStatus).isEqualTo("COMPLETED");
        assertThat(goodsStatus(goodsId, sellerToken)).isEqualTo("SOLD");

        // 8. 双方互评（TC-12：BR-07）+ 信用分（TC-30 口径：卖家作为 reviewee 收到的评分均值）
        // 买家评卖家 5 分（reviewee=seller），卖家评买家 4 分（reviewee=buyer）→ 卖家信用分 = 5.0
        postJson("/api/v1/reviews", "{\"orderId\":" + orderId + ",\"rating\":5,\"content\":\"发货快\"}", buyerToken);
        postJson("/api/v1/reviews", "{\"orderId\":" + orderId + ",\"rating\":4,\"content\":\"付款爽快\"}", sellerToken);
        String sellerProfile = getJson("/api/v1/users/" + sellerId + "/profile", null);
        Double credit = JsonPath.read(sellerProfile, "$.data.credit");
        assertThat(credit).isEqualTo(5.0);

        // 9. 买家留言：敏感词拒绝（TC-11/BR-11）→ 正常留言成功
        String sensitive = postJson("/api/v1/goods/" + goodsId + "/messages",
                "{\"content\":\"加微信详聊\"}", buyerToken);
        String sensitiveCode = JsonPath.read(sensitive, "$.code");
        assertThat(sensitiveCode).isEqualTo("B0401");
        String normal = postJson("/api/v1/goods/" + goodsId + "/messages",
                "{\"content\":\"教材已收到，好评\"}", buyerToken);
        String normalCode = JsonPath.read(normal, "$.code");
        assertThat(normalCode).isEqualTo("00000");

        // 10. 买家举报卖家（TC-31）→ 管理员处理（TC-33：不属实关闭）
        postJson("/api/v1/reports", "{\"targetType\":\"USER\",\"targetId\":" + sellerId
                + ",\"reason\":\"测试举报\",\"detail\":\"\"}", buyerToken);
        String pendingReports = getJson("/api/v1/admin/reports?status=PENDING", adminToken);
        assertThat(pendingReports).contains("测试举报");
        Long reportId = ((Number) JsonPath.read(pendingReports, "$.data.list[0].id")).longValue();
        postJson("/api/v1/admin/reports/" + reportId + "/handle",
                "{\"result\":\"DISMISSED\",\"note\":\"查证无违规\"}", adminToken);

        // 11. 卖家资料修改（TC-16：即时生效）
        String updated = postJson("/api/v1/users/me",
                "{\"nickname\":\"卖家小王Pro\",\"avatarUrl\":\"\",\"bio\":\"诚信经营\"}", sellerToken);
        String nickname = JsonPath.read(updated, "$.data.nickname");
        assertThat(nickname).isEqualTo("卖家小王Pro");

        // 12. 管理员看板（TC-35）：订单总数与库内一致
        String dashboard = getJson("/api/v1/admin/dashboard", adminToken);
        Long orderTotal = Long.valueOf(String.valueOf(JsonPath.read(dashboard, "$.data.orderTotal")));
        assertThat(orderTotal).isEqualTo(1);

        // 13. 越权回归（TC-09/NFR-SEC-02）：买家调管理接口 → A0302
        String forbidden = getJson("/api/v1/admin/goods/pending", buyerToken);
        String forbiddenCode = JsonPath.read(forbidden, "$.code");
        assertThat(forbiddenCode).isEqualTo("A0302");
    }

    /** 读取商品状态：非在售（LOCKED/SOLD）仅属主可见（FR-GOODS-06），须传卖家 token */
    private String goodsStatus(Long goodsId, String token) throws Exception {
        return JsonPath.read(getJson("/api/v1/goods/" + goodsId, token), "$.data.status");
    }
}