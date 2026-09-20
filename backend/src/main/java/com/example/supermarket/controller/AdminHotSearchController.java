package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.HotSearchRequest;
import com.example.supermarket.dto.HotSearchResponse;
import com.example.supermarket.service.HotSearchService;
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

/**
 * 后台热搜词管理：首页头部「热搜」那排词由这里维护（增删改、排序、启停）。
 */
@RestController
@RequestMapping("/admin/hot-searches")
public class AdminHotSearchController {

    private final HotSearchService hotSearchService;

    public AdminHotSearchController(HotSearchService hotSearchService) {
        this.hotSearchService = hotSearchService;
    }

    @GetMapping
    public ApiResponse<List<HotSearchResponse>> list() {
        return ApiResponse.ok(hotSearchService.listAll());
    }

    @PostMapping
    public ApiResponse<HotSearchResponse> create(@Valid @RequestBody HotSearchRequest request) {
        return ApiResponse.ok(hotSearchService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HotSearchResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody HotSearchRequest request
    ) {
        return ApiResponse.ok(hotSearchService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        hotSearchService.delete(id);
        return ApiResponse.ok(null);
    }
}
