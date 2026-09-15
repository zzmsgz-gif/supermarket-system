package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.FlashSaleRequest;
import com.example.supermarket.dto.FlashSaleResponse;
import com.example.supermarket.service.FlashSaleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台秒杀管理（/admin/** 已在 SecurityConfig 限定 ADMIN） */
@RestController
@RequestMapping("/admin/flash-sales")
public class AdminFlashSaleController {

    private final FlashSaleService flashSaleService;

    public AdminFlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @GetMapping
    public ApiResponse<List<FlashSaleResponse>> list(@RequestParam(required = false) Integer status) {
        return ApiResponse.ok(flashSaleService.listAdmin(status));
    }

    @PostMapping
    public ApiResponse<FlashSaleResponse> create(@Valid @RequestBody FlashSaleRequest request) {
        return ApiResponse.ok(flashSaleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FlashSaleResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody FlashSaleRequest request) {
        return ApiResponse.ok(flashSaleService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<FlashSaleResponse> updateStatus(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        Object raw = body.get("status");
        Integer status = raw == null ? null : Integer.valueOf(String.valueOf(raw));
        return ApiResponse.ok(flashSaleService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        flashSaleService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true, "id", id));
    }
}
