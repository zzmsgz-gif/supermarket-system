package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.SystemConfigRequest;
import com.example.supermarket.dto.SystemConfigResponse;
import com.example.supermarket.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台系统配置管理：按 key upsert（如把 festive_theme 在 daily / midautumn / none 之间切）。
 */
@RestController
@RequestMapping("/admin/system-config")
public class AdminSystemConfigController {

    private final SystemConfigService service;

    public AdminSystemConfigController(SystemConfigService service) {
        this.service = service;
    }

    @PutMapping("/{key}")
    public ApiResponse<SystemConfigResponse> upsert(
            @PathVariable("key") String key,
            @Valid @RequestBody SystemConfigRequest request
    ) {
        return ApiResponse.ok(service.upsert(key, request));
    }
}
