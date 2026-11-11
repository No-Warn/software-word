package com.campus.market.integration;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-03（BR-06）：并发 10 线程下单同一商品，恰 1 单成功、其余 B0302、商品 LOCKED、订单数=1。
 * 互斥由 tryLockGoods 条件更新 + SQLite 写串行化保证（02§2.6.1）。
 */
@SpringBootTest
class OrderConcurrencyIT {

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
        dbFile = Files.createTempFile("market-conc-", ".db");
        Files.deleteIfExists(dbFile);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbFile);
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(dbFile);
    }

    @Test
    void concurrentCreateOrder_onlyOneSucceeds_TC03() throws Exception {
        User seller = user("seller1@stu.example.edu.cn");
        User buyer = user("buyer1@stu.example.edu.cn");
        userMapper.insert(seller);
        userMapper.insert(buyer);

        Goods goods = new Goods();
        goods.setSellerId(seller.getId());
        goods.setCategoryId(1L);
        goods.setTitle("并发测试商品");
        goods.setDescription("");
        goods.setCondition("NEW");
        goods.setPrice(9.9);
        goods.setImagesJson("[\"/api/v1/files/a.jpg\"]");
        goods.setStatus(GoodsStatus.ON_SALE);
        goods.setDeleted(0);
        long now = System.currentTimeMillis();
        goods.setCreatedAt(now);
        goods.setUpdatedAt(now);
        goodsMapper.insert(goods);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Future<OrderVO>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return orderService.createOrder(buyer.getId(),
                                new OrderCreateRequest(goods.getId()));
                    } finally {
                        done.countDown();
                    }
                }));
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

            int success = 0;
            int lockedErrors = 0;
            for (Future<OrderVO> f : futures) {
                try {
                    f.get();
                    success++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof BizException biz
                            && biz.getErrorCode() == ErrorCode.GOODS_LOCKED) {
                        lockedErrors++;
                    }
                }
            }
            // BR-06：并发下恰 1 单成功，其余 9 个收到 B0302
            assertThat(success).as("成功下单数").isEqualTo(1);
            assertThat(lockedErrors).as("B0302 拒绝数").isEqualTo(threads - 1);
            // 商品被唯一成功者锁定
            assertThat(goodsMapper.selectById(goods.getId()).getStatus())
                    .isEqualTo(GoodsStatus.LOCKED);
            // 测试库内订单恰 1 条
            assertThat(orderMapper.selectCount(null)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
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
}