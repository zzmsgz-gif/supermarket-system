package com.example.supermarket.service;

import com.example.supermarket.dto.SystemConfigRequest;
import com.example.supermarket.dto.SystemConfigResponse;
import com.example.supermarket.entity.SystemConfig;
import com.example.supermarket.repository.SystemConfigRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通用 K/V 系统配置：前台公开读（festive_theme 等），后台按 key upsert。
 */
@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * 前台公开读：找不到返回 null（前端据此回落默认值，不抛错）。
     * 不缓存 null（unless），避免「迁移前先读」把缺失值一直缓成 null。
     */
    @Cacheable(value = "systemConfig", key = "#key", unless = "#result == null")
    @Transactional(readOnly = true)
    public SystemConfigResponse getByKey(String key) {
        return repository.findById(key)
                .map(SystemConfigResponse::from)
                .orElse(null);
    }

    /**
     * 后台写：key 不存在插入、存在则更新 value；description 仅在显式传了时才覆盖存量。
     */
    @CacheEvict(value = "systemConfig", key = "#key")
    @Transactional
    public SystemConfigResponse upsert(String key, SystemConfigRequest request) {
        SystemConfig entity = repository.findById(key).orElseGet(() -> {
            SystemConfig e = new SystemConfig();
            e.setConfigKey(key);
            return e;
        });
        entity.setConfigValue(request.getValue());
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        return SystemConfigResponse.from(repository.save(entity));
    }
}
