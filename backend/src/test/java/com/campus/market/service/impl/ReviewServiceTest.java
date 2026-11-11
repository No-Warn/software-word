package com.campus.market.service.impl;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.dto.request.ReviewCreateRequest;
import com.campus.market.dto.response.ReviewVO;
import com.campus.market.entity.Order;
import com.campus.market.entity.Review;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.ReviewRole;
import com.campus.market.mapper.OrderMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.SensitiveWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价服务测试（FR-REVIEW-01/02、BR-07；TC-12/TC-30 单元分支）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock
    ReviewMapper reviewMapper;
    @Mock
    OrderMapper orderMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    SensitiveWordService sensitiveWordService;

    ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(reviewMapper, orderMapper, userMapper, sensitiveWordService);
        when(reviewMapper.insert(any(Review.class))).thenReturn(1);
    }

    private Order completedOrder() {
        Order o = new Order();
        o.setId(50L);
        o.setBuyerId(1L);
        o.setSellerId(2L);
        o.setStatus(OrderStatus.COMPLETED);
        return o;
    }

    private Review review(int rating) {
        Review r = new Review();
        r.setRevieweeId(2L);
        r.setRating(rating);
        return r;
    }

    // TC-12：买家评卖家（role/reviewee 正确）
    @Test
    void create_buyerReviewsSeller_ok() {
        when(orderMapper.selectById(50L)).thenReturn(completedOrder());
        when(reviewMapper.selectCount(any())).thenReturn(0L);

        ReviewVO vo = service.create(1L, new ReviewCreateRequest(50L, 5, "很好"));

        assertThat(vo.role()).isEqualTo(ReviewRole.BUYER_TO_SELLER);
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getRevieweeId()).isEqualTo(2L);
        assertThat(captor.getValue().getRating()).isEqualTo(5);
    }

    // TC-12：卖家评买家
    @Test
    void create_sellerReviewsBuyer_ok() {
        when(orderMapper.selectById(50L)).thenReturn(completedOrder());
        when(reviewMapper.selectCount(any())).thenReturn(0L);

        ReviewVO vo = service.create(2L, new ReviewCreateRequest(50L, 4, "爽快"));

        assertThat(vo.role()).isEqualTo(ReviewRole.SELLER_TO_BUYER);
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getRevieweeId()).isEqualTo(1L);
    }

    // BR-07：仅 COMPLETED 订单可评
    @Test
    void create_rejectsNonCompleted_B0402() {
        Order paid = completedOrder();
        paid.setStatus(OrderStatus.PAID);
        when(orderMapper.selectById(50L)).thenReturn(paid);

        assertThatThrownBy(() -> service.create(1L, new ReviewCreateRequest(50L, 5, "x")))
                .hasMessageContaining("仅已完成订单可评价");
    }

    // NFR-SEC-02：非订单参与方 403
    @Test
    void create_rejectsNonParty_403() {
        when(orderMapper.selectById(50L)).thenReturn(completedOrder());
        assertThatThrownBy(() -> service.create(99L, new ReviewCreateRequest(50L, 5, "x")))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // BR-07：每订单每人 1 次
    @Test
    void create_rejectsDuplicate_B0402() {
        when(orderMapper.selectById(50L)).thenReturn(completedOrder());
        when(reviewMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1L, new ReviewCreateRequest(50L, 5, "x")))
                .hasMessageContaining("已评价过");
    }

    // TC-30：信用分口径 (5+5+3)/3 = 4.3
    @Test
    void creditOf_roundsToOneDecimal() {
        when(reviewMapper.selectList(any())).thenReturn(List.of(review(5), review(5), review(3)));
        assertThat(service.creditOf(2L)).isEqualTo(4.3);
    }

    // TC-30：无评价"暂无评价"（null）
    @Test
    void creditOf_nullWhenNoReviews() {
        when(reviewMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.creditOf(2L)).isNull();
    }
}