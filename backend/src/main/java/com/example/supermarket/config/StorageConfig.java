package com.example.supermarket.config;

import com.example.supermarket.storage.FileStorageService;
import com.example.supermarket.storage.LocalFileStorageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储装配 —— **只有本地磁盘一种实现**。
 *
 * <p>阿里云 OSS 已于 2026-09 按用户明确要求停用（避免产生费用），
 * 2026-11-20 彻底移除：{@code aliyun-sdk-oss} 依赖、{@code OssFileStorageService}
 * 实现类、{@code storage.oss} 配置块全部删除。
 *
 * <p>因此本类**不再读 {@code storage.type}**：无论环境变量里残留
 * {@code STORAGE_TYPE=oss} 还是 {@code OSS_ACCESS_KEY_*}，都只会拿到本地实现，
 * 不存在「配置写错就悄悄开始用云存储并计费」的路径。
 * 文件落在 {@code storage.local.dir}（生产为 {@code /opt/supermarket/uploads}），
 * 经 {@code /api/uploads/**} 访问。
 */
@Configuration
public class StorageConfig {

    @Bean
    public FileStorageService localFileStorageService() {
        return new LocalFileStorageService();
    }
}
