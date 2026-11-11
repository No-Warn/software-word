package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.request.ReportCreateRequest;
import com.campus.market.dto.response.ReportVO;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报受理接口（契约 API-27，02§2.5.2）。我的举报（API-28 GET /users/me/reports）按契约路径归属 UserController。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public Result<ReportVO> create(@Valid @RequestBody ReportCreateRequest req) {
        return Result.ok(reportService.create(SecurityUtils.currentUserId(), req));
    }
}