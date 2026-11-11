package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.request.MessageCreateRequest;
import com.campus.market.dto.response.MessageVO;
import com.campus.market.security.SecurityUtils;
import com.campus.market.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 留言接口（契约 API-22/23，02§2.5.2）。留言列表游客可见（权限矩阵 01§1.4.2）。
 * 我的留言（API-24 GET /users/me/messages）按契约路径归属 UserController。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/goods/{id}/messages")
    public Result<MessageVO> create(@PathVariable Long id, @Valid @RequestBody MessageCreateRequest req) {
        return Result.ok(messageService.create(SecurityUtils.currentUserId(), id, req));
    }

    @GetMapping("/goods/{id}/messages")
    public Result<List<MessageVO>> listByGoods(@PathVariable Long id) {
        return Result.ok(messageService.listByGoods(id));
    }
}