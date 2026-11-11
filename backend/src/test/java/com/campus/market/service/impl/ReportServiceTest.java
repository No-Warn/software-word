package com.campus.market.service.impl;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.dto.request.ReportCreateRequest;
import com.campus.market.dto.response.ReportVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Report;
import com.campus.market.entity.User;
import com.campus.market.enums.ReportStatus;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.ReportMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.mapper.UserMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 举报服务测试（FR-RPRT-01/02；TC-31/TC-32 单元分支）。
 * DoD 核心：重复举报提示"已受理"（B0403）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    ReportMapper reportMapper;
    @Mock
    GoodsMapper goodsMapper;
    @Mock
    UserMapper userMapper;

    ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportMapper, goodsMapper, userMapper);
        when(reportMapper.insert(any(Report.class))).thenReturn(1);
    }

    private ReportCreateRequest req(String type, Long targetId) {
        return new ReportCreateRequest(type, targetId, "违规内容", "补充说明");
    }

    // TC-31：受理成功 → PENDING
    @Test
    void create_ok_pending() {
        when(goodsMapper.selectById(100L)).thenReturn(new Goods());
        when(reportMapper.selectCount(any())).thenReturn(0L);

        ReportVO vo = service.create(1L, req("GOODS", 100L));

        assertThat(vo.status()).isEqualTo(ReportStatus.PENDING);
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getReporterId()).isEqualTo(1L);
        assertThat(captor.getValue().getTargetId()).isEqualTo(100L);
    }

    // TC-31 DoD 核心：重复举报（同举报人+同目标+PENDING）→ B0403
    @Test
    void create_rejectsDuplicatePending_B0403() {
        when(goodsMapper.selectById(100L)).thenReturn(new Goods());
        when(reportMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1L, req("GOODS", 100L)))
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_DUPLICATED);
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    void create_rejectsMissingGoods() {
        when(goodsMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(1L, req("GOODS", 999L)))
                .hasMessageContaining("被举报商品不存在");
    }

    @Test
    void create_rejectsMissingUser() {
        when(userMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(1L, req("USER", 999L)))
                .hasMessageContaining("被举报用户不存在");
    }

    @Test
    void create_rejectsBlankReason() {
        when(goodsMapper.selectById(100L)).thenReturn(new Goods());
        assertThatThrownBy(() -> service.create(1L, new ReportCreateRequest("GOODS", 100L, "  ", null)))
                .hasMessageContaining("举报原因必填");
    }

    // FR-RPRT-02：处理后状态与结论可见（TC-32）
    @Test
    @SuppressWarnings("unchecked")
    void listMine_showsHandleResult() {
        Report handled = new Report();
        handled.setId(200L);
        handled.setReporterId(1L);
        handled.setReason("违规");
        handled.setStatus(ReportStatus.RESOLVED);
        handled.setHandleNote("属实，已下架");
        handled.setHandledAt(System.currentTimeMillis());
        when(reportMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<Report> pg = inv.getArgument(0);
            pg.setRecords(List.of(handled));
            pg.setTotal(1);
            return pg;
        });

        var result = service.listMine(1L, 1, 20);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(result.getList().get(0).handleNote()).isEqualTo("属实，已下架");
    }
}