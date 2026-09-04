package com.example.supermarket.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地磁盘存储实现。仅用于本地开发 / 联调；生产环境请切换到 OssFileStorageService。
 * 文件落在 storage.local.dir 下，并按 bizType/年份 分目录；通过 WebConfig 的
 * 静态资源映射以 /api/uploads/** 对外提供。
 */
public class LocalFileStorageService implements FileStorageService {

    @Value("${storage.local.dir:./uploads}")
    private String baseDir;

    @Override
    public String store(String bizType, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        String year = String.valueOf(LocalDate.now().getYear());
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path targetDir = Paths.get(baseDir, bizType, year).toAbsolutePath();
        Files.createDirectories(targetDir);
        file.transferTo(targetDir.resolve(fileName).toFile());
        return "/api/uploads/" + bizType + "/" + year + "/" + fileName;
    }
}
