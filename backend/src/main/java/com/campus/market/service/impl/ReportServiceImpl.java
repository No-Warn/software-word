package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.common.PageResult;
import com.campus.market.dto.request.ReportCreateRequest;
import com.campus.market.dto.response.ReportVO;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Report;
import com.campus.market.entity.User;
import com.campus.market.enums.ReportStatus;
import com.campus.market.enums.ReportTargetType;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 举报服务（FR-RPRT-01/02；API-27/28）。
 * 受理校验：目标存在性（GOODS/USER 分支）→ reason 必填 → 重复举报拒绝（B0403，
 * 同一举报人 + 同一目标存在 PENDING 举报即提示"已受理"，FR-RPRT-01 验收口径）。
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;

    @Override
    public ReportVO create(Long reporterId, ReportCreateRequest req) {
        ReportTargetType type = parseType(req.targetType());
        // 目标存在性校验（分类型）
        if (type == ReportTargetType.GOODS) {
            Goods goods = goodsMapper.selectById(req.targetId());
            if (goods == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "被举报商品不存在");
            }
        } else {
            User user = userMapper.selectById(req.targetId());
            if (user == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "被举报用户不存在");
            }
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "举报原因必填");
        }
        // FR-RPRT-01：重复举报提示"已受理"（B0403）
        Long duplicated = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, type)
                .eq(Report::getTargetId, req.targetId())
                .eq(Report::getStatus, ReportStatus.PENDING));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ErrorCode.REPORT_DUPLICATED);            // B0403
        }
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(type);
        report.setTargetId(req.targetId());
        report.setReason(req.reason());
        report.setDetail(req.detail() == null ? "" : req.detail());
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(System.currentTimeMillis());
        reportMapper.insert(report);
        return ReportVO.from(report);
    }

    @Override
    public PageResult<ReportVO> listMine(Long userId, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);      // 02§2.5.1
        Page<Report> result = reportMapper.selectPage(new Page<>(p, s), new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .orderByDesc(Report::getCreatedAt));
        List<ReportVO> vos = result.getRecords().stream().map(ReportVO::from).toList();
        return PageResult.of(vos, result.getTotal(), p, s);
    }

    private ReportTargetType parseType(String type) {
        try {
            return ReportTargetType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "举报类型非法");   // 防御校验（注解兜底）
        }
    }
}