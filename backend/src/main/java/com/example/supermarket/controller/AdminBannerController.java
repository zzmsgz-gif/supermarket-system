package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.BannerRequest;
import com.example.supermarket.dto.BannerResponse;
import com.example.supermarket.service.BannerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台轮播位管理。 */
@RestController
@RequestMapping("/admin/banners")
public class AdminBannerController {

    private final BannerService bannerService;

    public AdminBannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ApiResponse<List<BannerResponse>> list() {
        return ApiResponse.ok(bannerService.listAll());
    }

    @PostMapping
    public ApiResponse<BannerResponse> create(@Valid @RequestBody BannerRequest request) {
        return ApiResponse.ok(bannerService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BannerResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody BannerRequest request
    ) {
        return ApiResponse.ok(bannerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return ApiResponse.ok(null);
    }
}
