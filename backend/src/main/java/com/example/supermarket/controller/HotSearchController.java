package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.HotSearchResponse;
import com.example.supermarket.service.HotSearchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页头部「热搜」词条（公开只读）。游客也要能看到，所以已在 SecurityConfig 放行。
 */
@RestController
@RequestMapping("/hot-searches")
public class HotSearchController {

    private final HotSearchService hotSearchService;

    public HotSearchController(HotSearchService hotSearchService) {
        this.hotSearchService = hotSearchService;
    }

    @GetMapping
    public ApiResponse<List<HotSearchResponse>> list() {
        return ApiResponse.ok(hotSearchService.listEnabled());
    }
}
