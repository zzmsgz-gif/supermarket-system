package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.StoreRequest;
import com.example.supermarket.dto.StoreResponse;
import com.example.supermarket.service.StoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台门店管理（/admin/** 已在 SecurityConfig 限定 ADMIN） */
@RestController
@RequestMapping("/admin/stores")
public class AdminStoreController {

    private final StoreService storeService;

    public AdminStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public ApiResponse<List<StoreResponse>> list() {
        return ApiResponse.ok(storeService.listAll());
    }

    @PostMapping
    public ApiResponse<StoreResponse> create(@Valid @RequestBody StoreRequest request) {
        return ApiResponse.ok(storeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StoreResponse> update(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return ApiResponse.ok(storeService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<StoreResponse> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("status");
        Byte status = raw == null ? null : Byte.valueOf(String.valueOf(raw));
        return ApiResponse.ok(storeService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        storeService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true, "id", id));
    }
}
