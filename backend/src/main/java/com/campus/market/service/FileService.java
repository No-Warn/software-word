package com.campus.market.service;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.config.MarketProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 本地图片存储（ADR-005 / BR-10 / NFR-SEC-03）：
 * - 大小上限 5MB：multipart 配置兜底 + Service 双保险；
 * - 格式校验：扩展名白名单 + 文件魔数（防改后缀绕过）；
 * - 文件名：服务端 UUID 生成，杜绝用户输入参与路径；
 * - 读取：normalize 后必须仍位于 images 根目录内（防路径穿越 / 绝对路径注入）。
 */
@Service
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "png", "webp");
    private static final long MAX_SIZE = 5L * 1024 * 1024;   // BR-10

    private final Path imagesRoot;

    public FileService(MarketProperties props) {
        this.imagesRoot = Paths.get(props.getDataDir(), "images").toAbsolutePath().normalize();
        try {
            Files.createDirectories(imagesRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建图片目录: " + imagesRoot, e);
        }
    }

    /**
     * 上传图片，返回可访问 URL（/api/v1/files/{name}，契约 API-08）。
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.IMAGE_INVALID, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.IMAGE_INVALID, "单张图片不超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ErrorCode.IMAGE_INVALID, "仅支持 jpg/png/webp 格式");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = imagesRoot.resolve(storedName);
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片保存失败");
        }
        // 落盘后按魔数校验真实格式，不符则删除并拒绝
        if (!magicNumberMatches(target, ext)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 清理失败不影响拒绝结果
            }
            throw new BizException(ErrorCode.IMAGE_INVALID, "文件内容与格式不符");
        }
        return "/api/v1/files/" + storedName;
    }

    /**
     * 读取图片（契约 API-09）：路径穿越/绝对路径注入防护（NFR-SEC-03 衍生）。
     */
    public Resource read(String name) {
        Path target = imagesRoot.resolve(name).normalize();
        if (!target.startsWith(imagesRoot) || !Files.isRegularFile(target)) {
            throw new BizException(ErrorCode.IMAGE_INVALID, "文件不存在");
        }
        return new FileSystemResource(target);
    }

    private boolean magicNumberMatches(Path target, String ext) {
        byte[] head;
        try (InputStream in = Files.newInputStream(target)) {
            head = in.readNBytes(12);
        } catch (IOException e) {
            return false;
        }
        return switch (ext) {
            case "jpg" -> head.length >= 3
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png" -> head.length >= 4
                    && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
            case "webp" -> head.length >= 12
                    && head[0] == 0x52 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x46
                    && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50;
            default -> false;
        };
    }
}