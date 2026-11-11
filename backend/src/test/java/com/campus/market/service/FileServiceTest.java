package com.campus.market.service;

import com.campus.market.common.BizException;
import com.campus.market.common.ErrorCode;
import com.campus.market.config.MarketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图片上传/读取测试（FR-GOODS-01 上传分支、BR-10、NFR-SEC-03 路径穿越；TC-18 上传专项）。
 */
class FileServiceTest {

    @TempDir
    Path tempDir;

    FileService service;

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @BeforeEach
    void setUp() {
        MarketProperties props = new MarketProperties();
        props.setDataDir(tempDir.toString());
        service = new FileService(props);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    @Test
    void uploadJpgPngWebpOk_andStoredOnDisk() throws Exception {
        String[][] cases = {{"a.jpg", "jpg"}, {"b.png", "png"}, {"c.webp", "webp"}};
        for (String[] c : cases) {
            byte[] bytes = switch (c[1]) {
                case "png" -> PNG;
                case "webp" -> WEBP;
                default -> JPEG;
            };
            String url = service.upload(file(c[0], bytes));
            assertThat(url).startsWith("/api/v1/files/");
            String stored = url.substring("/api/v1/files/".length());
            Path onDisk = tempDir.resolve("images").resolve(stored);
            assertThat(Files.exists(onDisk)).isTrue();
        }
    }

    @Test
    void rejectBadExtension() {
        assertThatThrownBy(() -> service.upload(file("shell.gif", JPEG)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅支持 jpg/png/webp");
    }

    @Test
    void rejectFakeMagic_noFileLeftBehind() {
        assertThatThrownBy(() -> service.upload(file("fake.jpg", "not an image".getBytes())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件内容与格式不符");
        // 落盘后校验失败必须清理：images 目录应为空
        try (var list = Files.list(tempDir.resolve("images"))) {
            assertThat(list.count()).isZero();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void rejectEmpty() {
        assertThatThrownBy(() -> service.upload(file("empty.jpg", new byte[0])))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件为空");
    }

    @Test
    void rejectOverSize_5MB() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        assertThatThrownBy(() -> service.upload(file("big.jpg", big)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void read_rejectsPathTraversal() {
        // 相对路径穿越
        assertThatThrownBy(() -> service.read("../user.db"))
                .isInstanceOf(BizException.class);
        // 绝对路径注入（Path.resolve 对绝对路径直接替换）
        assertThatThrownBy(() -> service.read("/etc/passwd"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void read_rejectsMissingFile() {
        assertThatThrownBy(() -> service.read("no-such-file.jpg"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件不存在");
    }
}