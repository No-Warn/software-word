package com.campus.market.integration;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.market.dto.request.OrderCreateRequest;
import com.campus.market.dto.response.OrderVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Order;
import com.campus.market.entity.User;
import com.campus.market.enums.GoodsStatus;
import com.campus.market.enums.OrderStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-05/TC-06 集成：超时订单被关闭并回滚（可复购）、关闭任务并发双跑恰关一次（先到先得）。
 * 时间加速口径：直接把订单 timeout_at 置为过去（压缩 BR-03 的 15 分钟窗口）。
 */
@SpringBootTest
class OrderTimeoutIT {

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
        dbFile = Files.createTempFile("market-timeout-", ".db");
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

    private void expireOrder(Long orderId) {
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getTimeoutAt, System.currentTimeMillis() - 1000));
    }

    // TC-05：超时订单被关闭并回滚，商品可复购
    @Test
    void tc05_timeoutClosesAndUnlocks_repurchasable() {
        User seller = user("tc05-seller@stu.example.edu.cn");
        User buyer = user("tc05-buyer@stu.example.edu.cn");
        userMapper.insert(seller);
        userMapper.insert(buyer);
        Goods goods = onSaleGoods(seller.getId(), "TC05 超时关闭商品");
        goodsMapper.insert(goods);

        OrderVO created = orderService.createOrder(buyer.getId(), new OrderCreateRequest(goods.getId()));
        assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.LOCKED);

        expireOrder(created.id());

        int closed = orderService.closeTimeoutOrders();
        assertThat(closed).isEqualTo(1);
        assertThat(orderMapper.selectById(created.id()).getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.ON_SALE);

        // TC-05 验收核心：超时后商品可复购
        OrderVO repurchased = orderService.createOrder(buyer.getId(), new OrderCreateRequest(goods.getId()));
        assertThat(repurchased.status()).isEqualTo(OrderStatus.PENDING_PAY);
        assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.LOCKED);
    }

    // TC-06：关闭任务并发双跑，条件更新先到先得，恰关一次且状态一致
    @Test
    void tc06_concurrentClose_doubleExecSafe() throws Exception {
        User seller = user("tc06-seller@stu.example.edu.cn");
        User buyer = user("tc06-buyer@stu.example.edu.cn");
        userMapper.insert(seller);
        userMapper.insert(buyer);
        Goods goods = onSaleGoods(seller.getId(), "TC06 并发关闭商品");
        goodsMapper.insert(goods);
        OrderVO created = orderService.createOrder(buyer.getId(), new OrderCreateRequest(goods.getId()));
        expireOrder(created.id());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return orderService.closeTimeoutOrders();
                    } finally {
                        done.countDown();
                    }
                }));
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

            int total = 0;
            for (Future<Integer> f : futures) {
                total += f.get();
            }
            // 先到先得：并发双跑恰关一次（第二次扫描为空或 markClosed 行数=0）
            assertThat(total).as("并发关闭总数").isEqualTo(1);
            assertThat(orderMapper.selectById(created.id()).getStatus()).isEqualTo(OrderStatus.CLOSED);
            assertThat(goodsMapper.selectById(goods.getId()).getStatus()).isEqualTo(GoodsStatus.ON_SALE);
        } finally {
            executor.shutdownNow();
        }
    }
}