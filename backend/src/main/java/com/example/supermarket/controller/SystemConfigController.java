package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.SystemConfigResponse;
import com.example.supermarket.service.SystemConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置（公开只读）。游客也要能读到（如首页氛围开关），已在 SecurityConfig 放行 GET。
 */
@RestController
@RequestMapping("/system-config")
public class SystemConfigController {

    private final SystemConfigService service;

    public SystemConfigController(SystemConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SystemConfigResponse> get(@RequestParam("key") String key) {
        return ApiResponse.ok(service.getByKey(key));
    }
}
