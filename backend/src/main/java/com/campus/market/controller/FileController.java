package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口（契约 API-08/09，02§2.5.2）：
 * - POST /files/image：需登录（SecurityConfig 非 GET 规则）；
 * - GET /files/{name}：公开（游客可见，权限矩阵 01§1.4.2）。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/image")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.upload(file));
    }

    @GetMapping("/{name}")
    public ResponseEntity<Resource> read(@PathVariable String name) {
        Resource resource = fileService.read(name);
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(resource.getFilename()))
                .body(resource);
    }

    private MediaType mediaTypeOf(String filename) {
        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}