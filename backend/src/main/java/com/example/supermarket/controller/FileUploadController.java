package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.storage.FileStorageService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
public class FileUploadController {

    private static final Set<String> ALLOWED_TYPES = Set.of("product", "avatar", "review");
    private static final List<String> ALLOWED_CONTENT =
            List.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_SIZE = 10 * 1024 * 1024;

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> upload(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "misc") String type
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的图片");
        }
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(400, "不支持的上传类型：" + type);
        }
        if (!ALLOWED_CONTENT.contains(file.getContentType())) {
            throw new BusinessException(400, "仅支持 JPG / PNG / WEBP / GIF 图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "图片大小不能超过 10MB");
        }
        if (type.equals("product") && !"ADMIN".equals(currentUser.getRole())) {
            throw new BusinessException(403, "仅管理员可上传商品 / 分类图片");
        }
        try {
            String url = fileStorageService.store(type, file);
            return ApiResponse.ok(Map.of("url", url));
        } catch (Exception e) {
            throw new BusinessException(500, "文件存储失败：" + e.getMessage());
        }
    }
}
