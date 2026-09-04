package com.example.supermarket.config;

import com.example.supermarket.storage.FileStorageService;
import com.example.supermarket.storage.LocalFileStorageService;
import com.example.supermarket.storage.OssFileStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据 storage.type 选择存储实现：
 *   - oss   : 阿里云 OSS
 *   - local : 本地磁盘（默认，缺省值）
 * 业务代码只依赖 FileStorageService 接口，切换存储无需改代码。
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "oss")
    public FileStorageService ossFileStorageService() {
        return new OssFileStorageService();
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
    public FileStorageService localFileStorageService() {
        return new LocalFileStorageService();
    }
}
