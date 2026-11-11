package com.campus.market.integration;

import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.Role;
import com.campus.market.enums.UserStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.OrderService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-25/TC-26 集成：取消回滚可复购、完成标记售出（02§2.6.6 事务一致性端到端证据）。
 */
@SpringBootTest
class OrderLifecycleIT {

    @Autowired
    OrderService orderService;
    @Autowired
    GoodsMapper goodsMapper;
    @Autowired
    OrderMapper orderMapper;
    @Autowired
    UserMapper userMapper;

    static Path dbFile;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("market-lifecycle-", ".db");
        Files.deleteIfExists(dbFile);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbFile);
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("$2a$10$placeholder");
        u.setNickname(email.split("@")[0]);
        u.setRole(Role.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setFailCount(0);
        u.setCreatedAt(System.currentTimeMillis());
        return u;
    }

    private Goods onSaleGoods(Long sellerId, String title) {
        Goods g = new Goods();
        g.setSellerId(sellerId);
        g.setCategoryId(1L);
        g.setTitle(title);
        g.setDescription("");
        g.setCondition("NEW");
        g.setPrice(9.9);
        g.setImagesJson("[\"/api/v1/files/a.jpg\"]");
        g.setStatus(GoodsStatus.ON_SALE);
        g.setDeleted(0);
        long now = System.currentTimeMillis();
        g.setCreatedAt(now);
        g.setUpdatedAt(now);
        return g;
    }

    @Test
    void cancelRestoresGoodsOnSaleAndRepurchasable_TC25() {
        User seller = user("tc25-seller@stu.example.edu.cn");
        User buyer = user("tc25-buyer@stu.example.edu.cn");
        userMapper.insert(seller);
        userMapper.insert(buyer);
        Goods goods = onSaleGoods(seller.getId(), "TC25 取消回滚商品");
        goodsMapper.insert(goods);

        // 下单 → 商品 LOCKED
        OrderVO created = orderService.createOrder(buyer.getId(), new OrderCreateRequest(goods.getId()));
        assertThat(created.status()).isNotNull();
        assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.LOCKED);

        // 取消 → 订单 CANCELLED + 商品回滚 ON_SALE（02§2.6.6 同事务）
        OrderVO cancelled = orderService.cancel(buyer.getId(), created.id());
        assertThat(cancelled.status()).isEqualTo(com.campus.market.enums.OrderStatus.CANCELLED);
        assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.ON_SALE);

        // TC-25 验收核心：取消后商品可复购
        OrderVO repurchased = orderService.createOrder(buyer.getId(), new OrderCreateRequest(goods.getId()));
        assertThat(repurchased.status()).isEqualTo(com.campus.market.enums.OrderStatus.PENDING_PAY);
        assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.LOCKED);
    }

    @Test
    void completeMarksGoodsSold_TC26() {
        User seller = user("tc26-seller@stu.example.edu.cn");
        User buyer = user("tc26-buyer@stu.example.edu.cn");
        userMapper.insert(seller);
        userMapper.insert(buyer);
        Goods goods = onSaleGoods(seller.getId(), "TC26 完成售出商品");
        goodsMapper.insert(goods);

        // 下单 → 支付 → 完成
        OrderVO created = orderService.createOrder(buyer.getId(), new OrderCreateRequest(goods.getId()));
        OrderVO paid = orderService.pay(buyer.getId(), created.id());
        assertThat(paid.status()).isEqualTo(com.campus.market.enums.OrderStatus.PAID);
        assertThat(paid.payVoucherNo()).matches("PAY\\d{18}");

        OrderVO completed = orderService.complete(buyer.getId(), created.id());
        assertThat(completed.status()).isEqualTo(com.campus.market.enums.OrderStatus.COMPLETED);
        assertThat(completed.completeTime()).isNotNull();
        assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.SOLD);

        // 幂等：重复确认返回当前态
        OrderVO again = orderService.complete(buyer.getId(), created.id());
        assertThat(again.status()).isEqualTo(com.campus.market.enums.OrderStatus.COMPLETED);
    }
}