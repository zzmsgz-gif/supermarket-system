package com.example.supermarket.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阿里云 OSS 存储实现（S3 兼容接口）。生产环境推荐。
 * 在 application.yml 中设置 storage.type=oss 并填入 endpoint / bucket / 密钥即可启用。
 */
public class OssFileStorageService implements FileStorageService {

    @Value("${storage.oss.endpoint:}")
    private String endpoint;

    @Value("${storage.oss.bucket:}")
    private String bucket;

    @Value("${storage.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${storage.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${storage.oss.base-url:}")
    private String baseUrl;

    @Override
    public String store(String bizType, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        String year = String.valueOf(LocalDate.now().getYear());
        String objectKey = bizType + "/" + year + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        try (InputStream in = file.getInputStream()) {
            OSS oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            try {
                oss.putObject(new PutObjectRequest(bucket, objectKey, in));
            } finally {
                oss.shutdown();
            }
        }

        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.replaceAll("/+$", "") + "/" + objectKey;
        }
        return endpoint.replaceAll("/+$", "") + "/" + bucket + "/" + objectKey;
    }
}
